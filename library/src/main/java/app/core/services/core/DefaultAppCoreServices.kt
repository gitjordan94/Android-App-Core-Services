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
import app.core.services.attribution.AttributionProvider
import app.core.services.attribution.AttributionServerClient
import app.core.services.billing.BillingClient
import app.core.services.common.isSystemInDarkTheme
import app.core.services.common.measureExecutionTime
import app.core.services.config.RemoteConfig
import app.core.services.config.RemoteConfigParams
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
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal class DefaultAppCoreServices(
    private val configuration: AppCoreServices.Configuration,
    compositeAnalytics: CompositeAnalytics,
    override val analytics: Analytics = compositeAnalytics,
    override val billingClient: BillingClient,
    override val deepLinkManager: DeepLinkManager,
    override val remoteConfig: RemoteConfig,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val appUpdateManager: AppUpdateManager,
    private val amplitudeAnalytics: AmplitudeAnalytics,
    private val attributionServerClient: AttributionServerClient?,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    private val preferencesDataStore: PreferencesDataStore,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val attributionProvider: AttributionProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
) : AppCoreServices {
    private val applicationScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    private var configurationResult: ConfigurationResult? = null

    init {
        val appsFlyerUID = appsFlyerAnalytics.appsFlyerUID

        Timber.d("AppsFlyer UID: $appsFlyerUID")

        if (attributionServerClient == null) {
            amplitudeAnalytics.setUserId(appsFlyerUID)
            firebaseAnalytics.setUserId(appsFlyerUID)
        }

        appsFlyerAnalytics.conversionDataFlow
            .filterNot { it is ConversionDataResult.Loading }
            .transform { result ->
                val previousValue = appsFlyerAnalytics.conversionDataFlow.value

                val wasAlreadyResolved =
                    previousValue is ConversionDataResult.Success || previousValue is ConversionDataResult.Error

                if (wasAlreadyResolved && result != previousValue) {
                    emit(result)
                }
            }
            .onEach { result ->
                val data = (result as? ConversionDataResult.Success)?.data
                analytics.logEvent("AF_CONVERSION_DATA_UPDATED", data)
            }
            .launchIn(applicationScope)
    }

    override suspend fun initialize(isFirstLaunch: Boolean?): ConfigurationResult {
        val configurationResult = configurationResult
        if (configurationResult != null) {
            return configurationResult
        }

        return getConfigurationResult(isFirstLaunch)
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

    private suspend fun getConfigurationResult(isFirstLaunch: Boolean?): ConfigurationResult {
        return withContext(coroutineDispatcher) {
            measureExecutionTime("Configuration") {
                onAttributionStarted()

                val storeCountryDeferred = getStoreCountryAsync()
                val purchasesDeferred = loadPurchasesAsync()

                val deviceInfo = async("Device Info") {
                    deviceInfoProvider.collectDeviceInfo()
                }

                val isFirstAppLaunch = isFirstLaunch != false
                        && preferencesDataStore.isFirstLaunch()

                if (isFirstAppLaunch) {
                    onFirstLaunch(deviceInfo.await())
                }

                val attributionDeferred = async {
                    measureExecutionTime("Attribution") {
                        val attribution = attributionProvider.provide()
                        if (attribution != null) {
                            onUserAttributed(attribution)
                        }
                        attribution
                    }
                }

                setupFirebaseAppInstanceId()

                val attribution = attributionDeferred.await() ?: Attribution(MediaSource())

                val storeCountry = withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                    storeCountryDeferred.await()
                }

                val userProperties = mapOf(
                    "network" to attribution.mediaSource.value,
                    "campaignName" to attribution.campaign,
                    "adGroupName" to attribution.adGroup,
                    "ad" to attribution.ad,
                    "deep_link_value" to attribution.deepLinkValue,
                    "attribution_source" to attribution.attributionSource?.value,
                    AnalyticsProperties.STORE_COUNTRY to (storeCountry ?: "unknown")
                )

                withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                    fetchRemoteConfigs(userProperties).await()
                }

                appUpdateManager.setMinSupportedVersionCode(
                    remoteConfig.getLong(RemoteConfigParams.MIN_SUPPORTED_APP_VERSION) ?: 0L
                )

                val purchases = withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                    purchasesDeferred.await()
                }

                appsFlyerAnalytics.appsFlyerUID?.let {
                    attributionServerClient?.install(it)
                }

                configurationResult = ConfigurationResult(
                    attribution = attribution,
                    purchases = purchases,
                    isFirstLaunch = isFirstAppLaunch
                )

                onAttributionFinished(attribution)

                Timber.d("Finished with $configurationResult.")

                configurationResult!!
            }
        }
    }

    private suspend fun onFirstLaunch(deviceInfo: DeviceInfo?) {
        val deviceInfoProperties = deviceInfo?.toAnalyticsProperties()?.takeIf { it.isNotEmpty() }

        if (deviceInfoProperties != null) {
            amplitudeAnalytics.setUserProperties(deviceInfoProperties)
        }

        amplitudeAnalytics.logEvent(AnalyticsEvents.FIRST_LAUNCH, deviceInfoProperties)

        amplitudeAnalytics.flush()
        amplitudeAnalytics.sendCohort()
        preferencesDataStore.setFirstLaunch(false)
    }

    private fun onAttributionStarted() {
        Timber.d("Attribution started.")

        val deviceTheme = if (configuration.context.isSystemInDarkTheme()) {
            "dark"
        } else {
            "light"
        }

        val properties = mapOf(
            "device_theme" to deviceTheme,
            "android_framework_version" to BuildConfig.SDK_VERSION
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

        analytics.logEvent(
            event = AnalyticsEvents.ATTRIBUTION,
            properties = properties
        )
    }

    private fun onAttributionFinished(attribution: Attribution) {
        Timber.d("Attribution finished.")

        val properties = mapOf(
            "network" to attribution.mediaSource.value,
            "campaignName" to attribution.campaign,
            "adGroupName" to attribution.adGroup,
            "ad" to attribution.ad,
            "deep_link_value" to attribution.deepLinkValue,
            "attribution_source" to attribution.attributionSource?.value
        )

        analytics.logEvent(
            event = AnalyticsEvents.ATTRIBUTION_FINISHED,
            properties = properties
        )
    }

    private suspend fun setupFirebaseAppInstanceId() {
        Timber.d("Setting up Firebase app instance ID.")

        try {
            val appInstanceId = firebaseAnalytics.getAppInstanceId()
            Timber.d("Firebase app instance ID: $appInstanceId")
            appsFlyerAnalytics.setAdditionalData(mapOf("firebase_app_instance_id" to appInstanceId))
        } catch (e: Throwable) {
            Timber.e(e, "Failed to get app instance ID.")
        }
    }

    private fun fetchRemoteConfigs(userProperties: Map<String, String?>) = async("Remote configs") {
        val fetchSuccess = remoteConfig.fetch(
            userId = amplitudeAnalytics.getUserId(),
            userProperties = userProperties
        )

        Timber.d("Remote config fetch success: $fetchSuccess")

        fetchSuccess
    }

    private fun loadPurchasesAsync() = async(name = "Purchases") {
        billingClient.getPurchases()
    }

    private fun getStoreCountryAsync() = async(name = "Store country") {
        billingClient.getStoreCountry()
    }

    private fun <T> async(name: String, block: suspend () -> T): Deferred<T?> {
        return applicationScope.async {
            measureExecutionTime(name) {
                try {
                    block()
                } catch (e: Throwable) {
                    Timber.e(e, "Failed to fetch '$name'")
                    null
                }
            }
        }
    }

    internal companion object {
        internal const val MAX_TIMEOUT_IN_MILLIS = 6_500L
    }
}