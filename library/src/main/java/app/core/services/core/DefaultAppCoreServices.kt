package app.core.services.core

import android.content.Context
import android.os.SystemClock
import app.core.services.AppCoreServices
import app.core.services.BuildConfig
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvents
import app.core.services.analytics.AnalyticsEvents.AF_CONVERSION_DATA_FAIL
import app.core.services.analytics.AnalyticsEvents.AF_CONVERSION_DATA_SUCCESS
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
import app.core.services.common.mapOfNotNull
import app.core.services.common.measureExecutionTime
import app.core.services.config.RemoteConfig
import app.core.services.config.RemoteConfigParams
import app.core.services.consent.Consent
import app.core.services.core.appsetid.AppSetIdProvider
import app.core.services.core.model.Attribution
import app.core.services.core.model.BootstrapResult
import app.core.services.core.model.LoadSources
import app.core.services.core.model.MediaSource
import app.core.services.core.util.toMap
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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

    private var bootstrapResultDeferred: Deferred<BootstrapResult>? = null

    private val consentFlow = MutableStateFlow<Consent?>(null)

    private var bootstrapResult: BootstrapResult? = null

    override fun setConsent(consent: Consent) {
        Timber.i(
            "[consent] set: analyticsStorage=%b, adStorage=%b",
            consent.analyticsStorage,
            consent.adStorage,
        )

        consentFlow.value = consent

        appsFlyerAnalytics.setConsent(consent)
        amplitudeAnalytics.setConsent(consent)
        firebaseAnalytics.setConsent(consent)
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

        observeUserId(appsFlyerUID)
        observeConversionData(appsFlyerUID)
        observeFirebaseAppInstanceId()

        startedDeferred.complete(Unit)
        Timber.i("[start] complete")
    }

    override suspend fun bootstrap(isFirstLaunch: Boolean?): BootstrapResult {
        Timber.i("[initialize] requested, isFirstLaunch=%s", isFirstLaunch)

        return mutex.withLock {
            val existing = bootstrapResultDeferred
            if (existing != null) {
                Timber.d("[initialize] already in progress or completed, awaiting existing result")
                existing
            } else {
                Timber.d("[initialize] starting fresh load")
                internalScope.async { load(isFirstLaunch) }.also {
                    bootstrapResultDeferred = it
                }
            }
        }.await()
    }

    override fun getBootstrapResult(): BootstrapResult? = bootstrapResult

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

    private suspend fun load(isFirstLaunch: Boolean?): BootstrapResult {
        Timber.d("[load] waiting for start()")
        startedDeferred.await()
        Timber.i("[load] begin")

        return coroutineScope {
            val consentSnapshot = consentFlow.value
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
                        // TODO:
                        mapOf()
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
                bootstrapResult = result

                Timber.i(
                    "[load] complete: firstLaunch=%b, storeCountry=%s, network=%s, purchases=%s",
                    result.isFirstLaunch,
                    result.storeCountry,
                    result.attribution.mediaSource.value,
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
        launch("application_install") {
            val uuid = appsFlyerAnalytics.appsFlyerUID
            if (uuid == null) {
                Timber.w("[application_install] skipped: appsflyer uid is null")
                return@launch
            }
            if (attributionServerClient == null) {
                Timber.d("[application_install] skipped: attribution server client is not configured")
                return@launch
            }
            Timber.d("[application_install] sending install for uid=%s", uuid)
            attributionServerClient.install(uuid)
            Timber.d("[application_install] install sent")
        },
        launch("first_launch") {
            val deviceInfo = sources.deviceInfo.awaitUntil(deadline)
            if (deviceInfo == null) {
                Timber.w("[first_launch] device info unavailable (timeout or failure)")
            }
            onFirstLaunch(deviceInfo)
        },
        launch("attribution_started") {
            val appSetId = sources.appSetId.awaitUntil(deadline)
            val advertisingId = sources.advertisingId.awaitUntil(deadline)
            sendAttributionStarted(appSetId, advertisingId)
        },
        launch("feature_flags") {
            val attribution = sources.attribution.awaitUntil(deadline) ?: run {
                Timber.w("[test_distribution] attribution unavailable, falling back to empty MediaSource")
                Attribution(MediaSource())
            }
            val storeCountry = sources.storeCountry.awaitUntil(deadline)
            val remoteConfigs = sources.remoteConfigs.awaitUntil(deadline)

            // TODO: remoteConfig.fetch()
        },
        launch("force_update") {
            sources.remoteConfigs.awaitUntil(deadline)

            runCatching {
                val minVersion = remoteConfig.getLong(RemoteConfigParams.MIN_SUPPORTED_APP_VERSION)
                minVersion?.let(appUpdateManager::setMinSupportedVersionCode)
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

    private fun CoroutineScope.launch(name: String, block: suspend () -> Unit): Job = launch {
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

    private fun buildResult(sources: LoadSources): BootstrapResult {
        val attribution = sources.attribution.getCompletedOrNull() ?: run {
            Timber.w("[build_result] attribution not completed in time, using empty MediaSource")
            Attribution(MediaSource())
        }
        val purchases = sources.purchases.getCompletedOrNull()
        val storeCountry = sources.storeCountry.getCompletedOrNull()
        val isFirstLaunch = sources.isFirstLaunch.getCompletedOrNull() ?: false

        if (purchases == null) Timber.w("[build_result] purchases not completed in time")
        if (storeCountry == null) Timber.w("[build_result] store country not completed in time")

        return BootstrapResult(
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUserId(appsFlyerUid: String?): Job {
        return consentFlow
            .map { it == null || it.analyticsStorage }
            .distinctUntilChanged()
            .onEach { analyticsAllowed ->
                val uid = if (analyticsAllowed) appsFlyerUid else null
                Timber.d("[user_id] analyticsAllowed=%b, uid=%s", analyticsAllowed, uid)
                setUserId(uid)
            }
            .launchIn(internalScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeConversionData(appsFlyerUid: String?): Job {
        Timber.d("[conversion_data] subscribing to AppsFlyer conversion data flow")
        return consentFlow
            .map { it == null || it.analyticsStorage }
            .distinctUntilChanged()
            .flatMapLatest { analyticsAllowed ->
                if (analyticsAllowed) {
                    appsFlyerAnalytics.conversionDataFlow.filterNotNull().distinctUntilChanged()
                } else {
                    Timber.d("[conversion_data] paused: analyticsStorage denied")
                    emptyFlow()
                }
            }
            .onEach { result ->
                when (result) {
                    is ConversionDataResult.Success -> {
                        Timber.i(
                            "[conversion_data] success, %d fields received",
                            result.data?.size ?: 0,
                        )
                        analytics.logEvent(
                            event = AF_CONVERSION_DATA_SUCCESS,
                            properties = result.data.orEmpty() + mapOf("appsflyer_uid" to appsFlyerUid)
                        )
                    }

                    is ConversionDataResult.Fail -> {
                        Timber.w("[conversion_data] failed: %s", result.errorMessage)
                        analytics.logEvent(
                            AF_CONVERSION_DATA_FAIL,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFirebaseAppInstanceId(): Job {
        return consentFlow
            .map { it == null || it.analyticsStorage }
            .distinctUntilChanged()
            .flatMapLatest { analyticsAllowed ->
                if (analyticsAllowed) {
                    flow<Unit> { attachFirebaseAppInstanceId() }
                } else {
                    Timber.d("[firebase_instance_id] paused: analyticsStorage denied")
                    emptyFlow()
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

        analytics.logEvent(AnalyticsEvents.ATTRIBUTION_STARTED, properties)
    }

    private fun onUserAttributed(attribution: Attribution) {
        Timber.i("[attribution] $attribution")

        val properties = attribution.toMap()
        analytics.setUserProperties(properties)
        analytics.logEvent(AnalyticsEvents.ATTRIBUTION, properties)
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