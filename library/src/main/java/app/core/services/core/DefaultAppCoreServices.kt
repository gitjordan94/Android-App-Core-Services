package app.core.services.core

import android.content.Context
import android.os.SystemClock
import app.core.services.AppCoreServices
import app.core.services.BuildConfig
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvents
import app.core.services.analytics.amplitude.AmplitudeAnalytics
import app.core.services.analytics.firebase.FirebaseAnalytics
import app.core.services.analytics.toAnalyticsProperties
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.appsflyer.ConversionDataResult
import app.core.services.appupdates.AppUpdateManager
import app.core.services.attribution.AdvertisingIdProvider
import app.core.services.attribution.AttributionProvider
import app.core.services.attribution.AttributionServerClient
import app.core.services.attribution.DeviceIdProvider
import app.core.services.attribution.model.AdvertisingId
import app.core.services.billing.BillingClient
import app.core.services.common.awaitUntil
import app.core.services.common.getCompletedOrNull
import app.core.services.common.isSystemInDarkTheme
import app.core.services.common.mapOfNotNull
import app.core.services.common.measureExecutionTime
import app.core.services.config.ExperimentVariant
import app.core.services.config.FirebaseRemoteConfig
import app.core.services.config.RemoteConfig
import app.core.services.config.RemoteConfigMatchingContext
import app.core.services.config.model.RemoteConfigParams.MIN_SUPPORTED_APP_VERSION
import app.core.services.config.model.RemoteConfigValue
import app.core.services.consent.Consent
import app.core.services.core.appsetid.AppSetIdProvider
import app.core.services.core.model.Attribution
import app.core.services.core.model.ConfigurationResult
import app.core.services.core.model.LoadSources
import app.core.services.core.model.MediaSource
import app.core.services.data.PreferencesDataStore
import app.core.services.deeplink.DeepLinkManager
import app.core.services.deviceinfo.DeviceInfo
import app.core.services.deviceinfo.DeviceInfoProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultAppCoreServices(
    override val analytics: Analytics,
    override val billingClient: BillingClient,
    override val remoteConfig: RemoteConfig,
    override val deepLinkManager: DeepLinkManager,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val appUpdateManager: AppUpdateManager,
    private val amplitudeAnalytics: AmplitudeAnalytics,
    private val attributionServerClient: AttributionServerClient?,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    private val configuration: AppCoreServices.Configuration,
    private val preferencesDataStore: PreferencesDataStore,
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val attributionProvider: AttributionProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val deviceIdProvider: DeviceIdProvider,
    private val appSetIdProvider: AppSetIdProvider,
    private val advertisingIdProvider: AdvertisingIdProvider,
) : AppCoreServices {
    private val mutex = Mutex()
    private val internalScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    private val isStarted = AtomicBoolean(false)

    private val startedDeferred = CompletableDeferred<Unit>()

    private var configurationResultDeferred: Deferred<ConfigurationResult>? = null

    @Volatile
    private var consent: Consent? = null

    private val mutableState = MutableStateFlow<ConfigurationResult?>(null)
    val state = mutableState.asStateFlow()

    override fun setConsent(consent: Consent) {
        Timber.i(
            "[consent] set: analyticsStorage=%b, adStorage=%b",
            consent.analyticsStorage,
            consent.adStorage,
        )

        this.consent = consent

        appsFlyerAnalytics.setConsent(consent)
        amplitudeAnalytics.setConsent(consent)
        firebaseAnalytics.setConsent(consent)

        val appsFlyerUID = appsFlyerAnalytics.appsFlyerUID

        if (consent.analyticsStorage) {
            Timber.d(
                "[consent] analyticsStorage granted, applying user id (appsflyer_uid=%s)",
                appsFlyerUID
            )
            setUserId(appsFlyerUID)
        } else {
            Timber.d("[consent] analyticsStorage denied, user id is not set")
        }
    }

    override fun start(context: Context) {
        if (!isStarted.compareAndSet(false, true)) {
            Timber.w("[start] skipped: already started")
            return
        }

        Timber.i("[start] begin")

        appsFlyerAnalytics.start(context)
        val appsFlyerUID = appsFlyerAnalytics.appsFlyerUID

        Timber.i("[start] AppsFlyer started, uid=%s", appsFlyerUID)

        val consentSnapshot = consent
        val analyticsAllowed = consentSnapshot == null || consentSnapshot.analyticsStorage

        if (analyticsAllowed) {
            Timber.d(
                "[start] analytics allowed (consent=%s), wiring user id and conversion data",
                if (consentSnapshot == null) "not_set" else "granted",
            )
            setUserId(appsFlyerUID)
            observeConversionData(appsFlyerUID)

            internalScope.launch {
                attachFirebaseAppInstanceId()
            }
        } else {
            Timber.d("[start] analytics denied by consent, skipping user id and conversion data wiring")
        }

        startedDeferred.complete(Unit)
        Timber.i("[start] complete")
    }

    override suspend fun initialize(isFirstLaunch: Boolean?): ConfigurationResult {
        Timber.i("[initialize] requested, isFirstLaunch=%s", isFirstLaunch)

        return mutex.withLock {
            val existing = configurationResultDeferred
            if (existing != null) {
                Timber.d("[initialize] already in progress or completed, awaiting existing result")
                existing
            } else {
                Timber.d("[initialize] starting fresh load")
                internalScope.async { load(isFirstLaunch) }.also {
                    configurationResultDeferred = it
                }
            }
        }.await()
    }

    override fun getConfigurationResult(): ConfigurationResult? = state.value

    override fun getUserId(): String? {
        return appsFlyerAnalytics.appsFlyerUID
    }

    override fun setExternalUserId(externalUserId: String?) {
        if (externalUserId != null) {
            Timber.i("[external_user_id] set: %s", externalUserId)
            amplitudeAnalytics.setUserId(externalUserId)
            attributionServerClient?.setExternalUserId(externalUserId)
        } else {
            Timber.i("[external_user_id] cleared, resetting amplitude")
            amplitudeAnalytics.reset()
        }
    }

    private fun setUserId(userId: String?) {
        if (configuration.attributionServerConfig?.externalAuthorization == true) {
            Timber.d("[user_id] skipped: external authorization is enabled")
            return
        }

        Timber.d("[user_id] applying to amplitude and firebase: %s", userId)
        amplitudeAnalytics.setUserId(userId)
        firebaseAnalytics.setUserId(userId)
    }

    private suspend fun load(isFirstLaunch: Boolean?): ConfigurationResult {
        Timber.d("[load] waiting for start()")
        startedDeferred.await()
        Timber.i("[load] begin")

        return coroutineScope {
            val consentSnapshot = consent
            Timber.d(
                "[load] consent snapshot: %s",
                consentSnapshot?.let { "analytics=${it.analyticsStorage}, ad=${it.adStorage}" }
                    ?: "not_set",
            )

            measureExecutionTime("framework_load") {
                val deadline = SystemClock.elapsedRealtime() + MAX_TIMEOUT_MS
                Timber.d("[load] deadline set to %d ms from now", MAX_TIMEOUT_MS)

                val sources = LoadSources(
                    isFirstLaunch = async("first_launch") {
                        isFirstLaunch != false && preferencesDataStore.isFirstLaunch()
                    },
                    attribution = async("attribution") {
                        attributionProvider.provide()
                    },
                    remoteConfigs = async("remote_configs") {
                        getRemoteConfigs()
                    },
                    deviceInfo = async("device_info") {
                        deviceInfoProvider.collectDeviceInfo()
                    },
                    appSetId = async("app_set_id") {
                        if (consentSnapshot == null || consentSnapshot.adStorage) {
                            appSetIdProvider.provide()
                        } else {
                            Timber.d("[app_set_id] skipped: adStorage denied")
                            null
                        }
                    },
                    advertisingId = async("advertising_id") {
                        if (consentSnapshot == null || consentSnapshot.adStorage) {
                            advertisingIdProvider.provide()
                        } else {
                            Timber.d("[advertising_id] skipped: adStorage denied")
                            null
                        }
                    },
                    purchases = async("purchases") {
                        billingClient.getPurchases()
                    },
                    storeCountry = async("store_country") {
                        billingClient.getStoreCountry()
                    }
                )

                launchDeviceIdAttach()
                val jobs = launch(sources, deadline)
                Timber.d("[load] launched %d post-source jobs, awaiting...", jobs.size)
                jobs.joinAll()

                val result = buildResult(sources)
                mutableState.value = result
                Timber.i(
                    "[load] complete: firstLaunch=%b, storeCountry=%s, network=%s, paywall=%s, purchases=%s",
                    result.isFirstLaunch,
                    result.storeCountry,
                    result.attribution.mediaSource.value,
                    result.activePaywall,
                    result.purchases
                )
                result
            }
        }
    }

    private fun CoroutineScope.launch(
        sources: LoadSources,
        deadline: Long,
    ): List<Job> = listOf(
        launchCatching("application_install") {
            val uuid = appsFlyerAnalytics.appsFlyerUID
            if (uuid == null) {
                Timber.w("[application_install] skipped: appsflyer uid is null")
                return@launchCatching
            }
            if (attributionServerClient == null) {
                Timber.d("[application_install] skipped: attribution server client is not configured")
                return@launchCatching
            }
            Timber.d("[application_install] sending install for uid=%s", uuid)
            attributionServerClient.install(uuid)
            Timber.d("[application_install] install sent")
        },
        launchCatching("first_launch") {
            val deviceInfo = sources.deviceInfo.awaitUntil(deadline)
            if (deviceInfo == null) {
                Timber.w("[first_launch] device info unavailable (timeout or failure)")
            }
            onFirstLaunch(deviceInfo)
        },
        launchCatching("attribution_started") {
            val appSetId = sources.appSetId.awaitUntil(deadline)
            val advertisingId = sources.advertisingId.awaitUntil(deadline)
            sendAttributionStarted(appSetId, advertisingId)
        },
        launchCatching("test_distribution") {
            val attribution = sources.attribution.awaitUntil(deadline) ?: run {
                Timber.w("[test_distribution] attribution unavailable, falling back to empty MediaSource")
                Attribution(MediaSource())
            }
            val storeCountry = sources.storeCountry.awaitUntil(deadline)
            val remoteConfigs = sources.remoteConfigs.awaitUntil(deadline)
            val matching = RemoteConfigMatchingContext(attribution, storeCountry)

            if (remoteConfigs != null && remoteConfig is FirebaseRemoteConfig) {
                Timber.d("[test_distribution] applying matching context to FirebaseRemoteConfig")
                remoteConfig.remoteConfigMatchingContext = matching
            } else if (remoteConfigs == null) {
                Timber.w("[test_distribution] remote configs unavailable, matching context not applied")
            }

            trackTestDistribution(
                isFirstAppLaunch = sources.isFirstLaunch.await() ?: false,
                storeCountry = storeCountry,
                attribution = attribution,
                experiments = resolveExperimentVariants(remoteConfigs, matching),
            )
        },
        launchCatching("force_update") {
            sources.remoteConfigs.awaitUntil(deadline)

            runCatching {
                val minVersion = remoteConfig.getLong(MIN_SUPPORTED_APP_VERSION)
                appUpdateManager.setMinSupportedVersionCode(minVersion)
                Timber.i("[force_update] min supported version applied: %d", minVersion)
            }.onFailure {
                Timber.e(it, "[force_update] failed to apply min supported version")
            }
        }
    )

    private fun CoroutineScope.launchDeviceIdAttach() {
        launch {
            try {
                if (attributionServerClient == null) {
                    Timber.d("[device_id_attach] skipped: attribution server client is not configured")
                    return@launch
                }
                val installUserId = attributionServerClient.getInstallUserId()
                if (installUserId != null) {
                    Timber.d("[device_id_attach] skipped: install user id already present")
                    return@launch
                }
                val deviceId = deviceIdProvider.provide()
                Timber.d("[device_id_attach] applying device id to amplitude: %s", deviceId)
                amplitudeAnalytics.setDeviceId(deviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "[device_id_attach] failed")
            }
        }
    }

    private fun CoroutineScope.launchCatching(
        name: String,
        block: suspend () -> Unit
    ): Job = launch {
        val started = SystemClock.elapsedRealtime()
        Timber.d("[%s] start", name)
        try {
            block()
            Timber.d("[%s] done in %d ms", name, SystemClock.elapsedRealtime() - started)
        } catch (e: CancellationException) {
            Timber.d("[%s] cancelled after %d ms", name, SystemClock.elapsedRealtime() - started)
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "[%s] failed after %d ms", name, SystemClock.elapsedRealtime() - started)
        }
    }

    private fun buildResult(sources: LoadSources): ConfigurationResult {
        val attribution = sources.attribution.getCompletedOrNull() ?: run {
            Timber.w("[build_result] attribution not completed in time, using empty MediaSource")
            Attribution(MediaSource())
        }
        val purchases = sources.purchases.getCompletedOrNull()
        val storeCountry = sources.storeCountry.getCompletedOrNull()
        val isFirstLaunch = sources.isFirstLaunch.getCompletedOrNull() ?: false
        val activePaywall = remoteConfig.getActivePaywallName()

        if (purchases == null) Timber.w("[build_result] purchases not completed in time")
        if (storeCountry == null) Timber.w("[build_result] store country not completed in time")

        return ConfigurationResult(
            activePaywall = activePaywall,
            attribution = attribution,
            purchases = purchases,
            storeCountry = storeCountry,
            isFirstLaunch = isFirstLaunch,
        )
    }

    private suspend fun onFirstLaunch(deviceInfo: DeviceInfo?) {
        val deviceInfoProperties =
            deviceInfo?.toAnalyticsProperties()?.takeIf { it.isNotEmpty() }

        if (deviceInfoProperties != null) {
            Timber.d("[first_launch] applying %d device info properties", deviceInfoProperties.size)
            amplitudeAnalytics.setUserProperties(deviceInfoProperties)
        } else {
            Timber.d("[first_launch] no device info properties to apply")
        }

        Timber.i("[first_launch] logging FIRST_LAUNCH event")
        amplitudeAnalytics.logEvent(AnalyticsEvents.FIRST_LAUNCH, deviceInfoProperties)

        amplitudeAnalytics.flush()
        amplitudeAnalytics.sendCohort()
        preferencesDataStore.setFirstLaunch(false)
        Timber.d("[first_launch] flushed, cohort sent, flag persisted")
    }

    private fun observeConversionData(appsFlyerUid: String?): Job {
        Timber.d("[conversion_data] subscribing to AppsFlyer conversion data flow")
        return appsFlyerAnalytics.conversionDataFlow
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { result ->
                when (result) {
                    is ConversionDataResult.Success -> {
                        Timber.i(
                            "[conversion_data] success, %d fields received",
                            result.data?.size ?: 0,
                        )
                        analytics.logEvent(
                            AnalyticsEvents.AF_CONVERSION_DATA_SUCCESS,
                            properties = result.data.orEmpty() + mapOf("appsflyer_uid" to appsFlyerUid)
                        )
                    }

                    is ConversionDataResult.Fail -> {
                        Timber.w("[conversion_data] failed: %s", result.errorMessage)
                        analytics.logEvent(
                            AnalyticsEvents.AF_CONVERSION_DATA_FAIL,
                            properties = mapOf(
                                "appsflyer_uid" to appsFlyerUid,
                                "error" to result.errorMessage
                            )
                        )
                    }
                }
            }
            .launchIn(internalScope)
    }

    private suspend fun attachFirebaseAppInstanceId() {
        Timber.d("[firebase_instance_id] requesting app instance id")

        try {
            val appInstanceId = firebaseAnalytics.getAppInstanceId()

            if (appInstanceId == null) {
                Timber.w("[firebase_instance_id] received null, skipping AppsFlyer attach")
                return
            }

            Timber.d("[firebase_instance_id] received: %s, attaching to AppsFlyer", appInstanceId)
            appsFlyerAnalytics.setAdditionalData(mapOf("firebase_app_instance_id" to appInstanceId))
        } catch (e: Throwable) {
            Timber.e(e, "[firebase_instance_id] failed to obtain app instance id")
        }
    }

    private suspend fun getRemoteConfigs(): Map<String, RemoteConfigValue> {
        try {
            remoteConfig.fetch()
            Timber.d("[remote_configs] fetched successfully")
        } catch (e: Throwable) {
            Timber.e(e, "[remote_configs] fetch failed, using cached values")
        }

        val configs = remoteConfig.getAll()

        Timber.d(
            "[remote_configs] loaded count=%d, keys=[%s]",
            configs.size,
            configs.keys.joinToString(),
        )

        return configs
    }

    private fun resolveExperimentVariants(
        configs: Map<String, RemoteConfigValue>?,
        matchingContext: RemoteConfigMatchingContext,
    ): Map<String, String> {
        if (configs.isNullOrEmpty()) {
            Timber.w("[ab_tests] no configs received, returning empty assignments")
            return emptyMap()
        }

        val parameters = configuration.remoteConfigParameters.parameters

        val configsWithTarget = configs.filter {
            parameters[it.key]?.target != null
        }

        if (configsWithTarget.isEmpty()) {
            Timber.d("[ab_tests] no targeted configs found out of %d total", configs.size)
            return emptyMap()
        }

        Timber.d(
            "[ab_tests] processing %d targeted configs out of %d total",
            configsWithTarget.size,
            configs.size,
        )

        val experimentAssignments = configsWithTarget.mapValues {
            val parameter = parameters[it.key]
            val rawValue = it.value.rawValue

            when {
                rawValue.isNullOrBlank() -> ExperimentVariant.NONE
                parameter?.target?.matches(matchingContext) != true -> ExperimentVariant.NONE
                rawValue.startsWith(ExperimentVariant.NONE_PREFIX) -> ExperimentVariant.NONE
                else -> rawValue
            }
        }

        val activeCount = experimentAssignments.count { it.value != ExperimentVariant.NONE }
        Timber.i(
            "[ab_tests] resolved: total=%d, active=%d",
            experimentAssignments.size,
            activeCount,
        )
        Timber.d(
            "[ab_tests] assignments:\n%s",
            experimentAssignments.entries.joinToString(separator = "\n") { "  ${it.key} = ${it.value}" }
        )

        return experimentAssignments
    }

    private fun sendAttributionStarted(
        appSetId: String?,
        advertisingId: AdvertisingId?,
    ) {
        val properties = mapOfNotNull(
            "android_framework_version" to BuildConfig.SDK_VERSION,
            "appsflyer_sdk_version" to BuildConfig.AF_SDK_VERSION,
            "appsflyer_uid" to appsFlyerAnalytics.appsFlyerUID,
            "app_set_id" to appSetId,
            "advertising_id" to advertisingId?.id,
            "is_limit_ad_tracking_enabled" to advertisingId?.isLimitAdTrackingEnabled,
        )

        Timber.i(
            "[attribution_started] uid=%s, app_set_id=%s, advertising_id=%s, limit_ad_tracking=%s",
            appsFlyerAnalytics.appsFlyerUID,
            appSetId,
            advertisingId?.id,
            advertisingId?.isLimitAdTrackingEnabled,
        )

        analytics.setUserProperties(properties)
        analytics.logEvent(
            event = AnalyticsEvents.FRAMEWORK_ATTRIBUTION_STARTED,
            properties = properties,
        )
    }

    private fun trackTestDistribution(
        isFirstAppLaunch: Boolean,
        storeCountry: String?,
        attribution: Attribution,
        experiments: Map<String, String>,
    ) {
        val attributionProperties = mapOfNotNull(
            "network" to attribution.mediaSource.value,
            "campaignName" to attribution.campaign,
            "adGroupName" to attribution.adGroup,
            "ad" to attribution.ad,
            "deep_link_value" to attribution.deepLinkValue
        )

        val eventProperties = attributionProperties + experiments

        val userProperties = buildMap {
            if (isFirstAppLaunch && attributionProperties.isNotEmpty()) {
                putAll(attributionProperties)
            }

            putAll(experiments)

            put("store_country", storeCountry ?: "unknown")

            put(
                "device_theme", if (configuration.context.isSystemInDarkTheme()) {
                    "dark"
                } else {
                    "light"
                }
            )

            attribution.attributionSource?.let { attributionSource ->
                put("attribution_source", attributionSource.value)
            }

            put("android_framework_version", BuildConfig.SDK_VERSION)
        }

        amplitudeAnalytics.setUserProperties(userProperties)
        amplitudeAnalytics.logEvent(AnalyticsEvents.TEST_DISTRIBUTION, eventProperties)
        amplitudeAnalytics.flush()

        Timber.i(
            "[test_distribution] sent: firstLaunch=%b, storeCountry=%s, network=%s, experiments=%d, userProps=%d",
            isFirstAppLaunch,
            storeCountry,
            attribution.mediaSource.value,
            experiments.size,
            userProperties.size,
        )
        Timber.d("[test_distribution] event properties: %s", eventProperties)
    }

    private fun <T> async(name: String, block: suspend () -> T): Deferred<T?> {
        return internalScope.async {
            measureExecutionTime(name) {
                try {
                    val result = block()
                    Timber.d("[%s] loaded: %s", name, result)
                    result
                } catch (e: CancellationException) {
                    Timber.d("[%s] cancelled", name)
                    throw e
                } catch (e: Throwable) {
                    Timber.e(e, "[%s] load failed", name)
                    null
                }
            }
        }
    }

    internal companion object {
        const val MAX_TIMEOUT_MS = 6_500L
    }
}