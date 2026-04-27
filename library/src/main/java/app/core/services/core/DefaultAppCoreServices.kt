package app.core.services.core

import app.core.services.AppCoreServices
import app.core.services.BuildConfig
import app.core.services.amplitude.analytics.AmplitudeAnalytics
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvents
import app.core.services.analytics.AnalyticsProperties
import app.core.services.analytics.CompositeAnalytics
import app.core.services.analytics.toAnalyticsProperties
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.appsflyer.ConversionDataResult
import app.core.services.appupdates.AppUpdateManager
import app.core.services.attribution.AdvertisingIdProvider
import app.core.services.attribution.AttributionProvider
import app.core.services.attribution.AttributionServerClient
import app.core.services.attribution.DeviceIdProvider
import app.core.services.billing.BillingClient
import app.core.services.common.isSystemInDarkTheme
import app.core.services.common.mapOfNotNull
import app.core.services.common.measureExecutionTime
import app.core.services.config.RemoteConfig
import app.core.services.config.RemoteConfigParams
import app.core.services.core.appsetid.AppSetIdProvider
import app.core.services.core.model.Attribution
import app.core.services.core.model.ConfigurationResult
import app.core.services.core.model.MediaSource
import app.core.services.data.PreferencesDataStore
import app.core.services.deeplink.DeepLinkManager
import app.core.services.deviceinfo.DeviceInfo
import app.core.services.deviceinfo.DeviceInfoProvider
import app.core.services.firebase.FirebaseAnalytics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal class DefaultAppCoreServices(
    override val billingClient: BillingClient,
    override val remoteConfig: RemoteConfig,
    override val deepLinkManager: DeepLinkManager,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val appUpdateManager: AppUpdateManager,
    private val amplitudeAnalytics: AmplitudeAnalytics,
    private val attributionServerClient: AttributionServerClient?,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    compositeAnalytics: CompositeAnalytics,
    private val configuration: AppCoreServices.Configuration,
    private val preferencesDataStore: PreferencesDataStore,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val attributionProvider: AttributionProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val deviceIdProvider: DeviceIdProvider,
    private val appSetIdProvider: AppSetIdProvider,
    private val advertisingIdProvider: AdvertisingIdProvider,
) : AppCoreServices {
    private val applicationScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    override val analytics: Analytics = compositeAnalytics

    private val initializationMutex = Mutex()
    private var configurationResultDeferred: Deferred<ConfigurationResult>? = null

    private var configurationResult: ConfigurationResult? = null

    init {
        val appsFlyerUID = appsFlyerAnalytics.appsFlyerUID
        Timber.d("AppsFlyer UID: $appsFlyerUID")

        initUserId(appsFlyerUID)
        observeConversionData(appsFlyerUID)
    }

    private fun initUserId(appsFlyerUID: String?) {
        if (attributionServerClient != null) return

        amplitudeAnalytics.setUserId(appsFlyerUID)
        firebaseAnalytics.setUserId(appsFlyerUID)
    }

    private fun observeConversionData(appsFlyerUid: String?) {
        appsFlyerAnalytics.conversionDataFlow
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { result ->
                when (result) {
                    is ConversionDataResult.Success -> {
                        analytics.logEvent(
                            AnalyticsEvents.AF_CONVERSION_DATA_SUCCESS,
                            properties = result.data.orEmpty() + mapOf("appsflyer_uid" to appsFlyerUid)
                        )
                    }

                    is ConversionDataResult.Fail -> {
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
            .launchIn(applicationScope)
    }

    override suspend fun initialize(isFirstLaunch: Boolean?): ConfigurationResult {
        return initializationMutex.withLock {
            configurationResultDeferred
                ?: applicationScope.async { awaitConfiguration(isFirstLaunch) }
                    .also { configurationResultDeferred = it }
        }.await()
    }

    override fun getConfigurationResult(): ConfigurationResult? {
        return configurationResult
    }

    override fun getUserId(): String? {
        return appsFlyerAnalytics.appsFlyerUID
    }

    override fun setExternalUserId(externalUserId: String?) {
        Timber.d("Setting external user ID: $externalUserId.")

        if (externalUserId != null) {
            amplitudeAnalytics.setUserId(externalUserId)
            attributionServerClient?.setExternalUserId(externalUserId)
        } else {
            amplitudeAnalytics.reset()
        }
    }

    private suspend fun awaitConfiguration(isFirstLaunch: Boolean?): ConfigurationResult {
        return withContext(coroutineDispatcher) {
            measureExecutionTime("Configuration") {
                val isFirstAppLaunch = isFirstLaunch != false
                        && preferencesDataStore.isFirstLaunch()

                val attributionDeferred = async {
                    measureExecutionTime("Attribution") {
                        attributionProvider.provide()
                    }
                }

                val deviceInfoDeferred = asyncOrNull("Device Info") {
                    deviceInfoProvider.collectDeviceInfo()
                }

                val purchasesDeferred = asyncOrNull(name = "Purchases") {
                    withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                        billingClient.getPurchases()
                    }
                }

                val storeCountryDeferred = asyncOrNull(name = "Store country") {
                    withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                        billingClient.getStoreCountry()
                    }
                }

                launch {
                    if (attributionServerClient != null) {
                        if (attributionServerClient.getInstallUserId() == null) {
                            amplitudeAnalytics.setDeviceId(deviceIdProvider.provide())
                        }
                    }
                }

                if (isFirstAppLaunch) {
                    onFirstLaunch(deviceInfoDeferred.await())
                }

                onAttributionStarted()

                attachFirebaseAppInstanceId()

                appsFlyerAnalytics.appsFlyerUID?.let {
                    attributionServerClient?.install(it)
                }

                val attribution = attributionDeferred.await() ?: run {
                    Timber.w("Attribution is null, fallback to empty attribution")
                    Attribution(MediaSource())
                }

                onUserAttributed(attribution)

                val storeCountry = storeCountryDeferred.await()

                val userProperties = mapOf(
                    "network" to attribution.mediaSource.value,
                    "campaignName" to attribution.campaign,
                    "adGroupName" to attribution.adGroup,
                    "ad" to attribution.ad,
                    "deep_link_value" to attribution.deepLinkValue,
                    "attribution_source" to attribution.attributionSource?.value,
                    AnalyticsProperties.STORE_COUNTRY to (storeCountry ?: "unknown")
                )

                fetchRemoteConfigs(userProperties)

                remoteConfig.getLong(RemoteConfigParams.MIN_SUPPORTED_APP_VERSION)
                    ?.let(appUpdateManager::setMinSupportedVersionCode)

                val purchases = purchasesDeferred.await()

                val result = ConfigurationResult(
                    attribution = attribution,
                    purchases = purchases,
                    storeCountry = storeCountry,
                    isFirstLaunch = isFirstAppLaunch
                ).also { configurationResult = it }

                onFrameworkFinished(result)

                result
            }
        }
    }

    private suspend fun onFirstLaunch(deviceInfo: DeviceInfo?) {
        val deviceInfoProperties =
            deviceInfo?.toAnalyticsProperties()?.takeIf { it.isNotEmpty() }

        if (deviceInfoProperties != null) {
            amplitudeAnalytics.setUserProperties(deviceInfoProperties)
        }

        amplitudeAnalytics.logEvent(AnalyticsEvents.FIRST_LAUNCH, deviceInfoProperties)

        amplitudeAnalytics.flush()
        amplitudeAnalytics.sendCohort()
        preferencesDataStore.setFirstLaunch(false)
    }

    private suspend fun attachFirebaseAppInstanceId() {
        Timber.d("Setting up Firebase app instance ID.")

        try {
            val appInstanceId = firebaseAnalytics.getAppInstanceId()
            Timber.d("Firebase app instance ID: $appInstanceId")
            appsFlyerAnalytics.setAdditionalData(mapOf("firebase_app_instance_id" to appInstanceId))
        } catch (e: Throwable) {
            Timber.e(e, "Failed to get app instance ID.")
        }
    }

    private suspend fun onAttributionStarted() {
        Timber.d("Attribution started.")

        val deviceTheme = if (configuration.context.isSystemInDarkTheme()) {
            "dark"
        } else {
            "light"
        }

        val appSetIdDeferred = asyncOrNull("App Set ID") {
            withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                appSetIdProvider.provide()
            }
        }

        val advertisingIdDeferred = asyncOrNull("Advertising ID") {
            advertisingIdProvider.provide()
        }

        val advertisingId = advertisingIdDeferred.await()

        val properties = mapOfNotNull(
            "device_theme" to deviceTheme,
            "android_framework_version" to BuildConfig.SDK_VERSION,
            "appsflyer_sdk_version" to BuildConfig.AF_SDK_VERSION,
            "appsflyer_uid" to appsFlyerAnalytics.appsFlyerUID,
            "app_set_id" to appSetIdDeferred.await(),
            "advertising_id" to advertisingId?.id,
            "is_limit_ad_tracking_enabled" to advertisingId?.isLimitAdTrackingEnabled,
        )

        analytics.setUserProperties(properties)
        analytics.logEvent(AnalyticsEvents.ATTRIBUTION_STARTED, properties)
    }

    private fun onUserAttributed(attribution: Attribution) {
        Timber.d("User attributed.")

        val properties = mapOf(
            "network" to attribution.mediaSource.value,
            "campaignName" to attribution.campaign,
            "adGroupName" to attribution.adGroup,
            "ad" to attribution.ad,
            "deep_link_value" to attribution.deepLinkValue,
            "attribution_source" to attribution.attributionSource?.value
        )

        analytics.setUserProperties(properties)

        analytics.logEvent(
            event = AnalyticsEvents.ATTRIBUTION,
            properties = properties
        )
    }

    private fun onFrameworkFinished(configuration: ConfigurationResult) {
        Timber.d("Attribution finished.")

        val attribution = configuration.attribution

        val properties = mapOf(
            "network" to attribution.mediaSource.value,
            "campaignName" to attribution.campaign,
            "adGroupName" to attribution.adGroup,
            "ad" to attribution.ad,
            "deep_link_value" to attribution.deepLinkValue,
            "attribution_source" to attribution.attributionSource?.value,
            "store_country" to (configuration.storeCountry ?: "unknown")
        )

        analytics.setUserProperties(properties)

        analytics.logEvent(
            event = AnalyticsEvents.FRAMEWORK_FINISHED,
            properties = properties
        )
    }

    private suspend fun fetchRemoteConfigs(userProperties: Map<String, String?>) {
        val fetchSuccess = remoteConfig.fetch(
            userId = amplitudeAnalytics.getUserId(),
            userProperties = userProperties
        )

        Timber.d("Remote config fetch success: $fetchSuccess")
    }

    private fun <T> asyncOrNull(
        name: String,
        block: suspend () -> T
    ): Deferred<T?> {
        return applicationScope.async {
            measureExecutionTime(name) {
                try {
                    val result = block()
                    Timber.d("%s loaded. isNull=%s", name, result == null)
                    result
                } catch (e: Throwable) {
                    Timber.e(e, "Failed to load %s", name)
                    null
                }
            }
        }
    }

    internal companion object {
        const val MAX_TIMEOUT_IN_MILLIS = 6_500L
    }
}