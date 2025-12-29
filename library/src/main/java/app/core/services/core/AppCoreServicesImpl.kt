package app.core.services.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.core.services.AppCoreServices
import app.core.services.BuildConfig
import app.core.services.analytics.Analytics
import app.core.services.analytics.CompositeAnalytics
import app.core.services.analytics.amplitude.AmplitudeAnalytics
import app.core.services.analytics.firebase.FirebaseAnalytics
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.appsflyer.ConversionDataResult
import app.core.services.appsflyer.attribution.AppsFlyerAttributionProvider
import app.core.services.appupdates.AppUpdateManager
import app.core.services.attribution.AttributionProvider
import app.core.services.attribution.AttributionServerClient
import app.core.services.attribution.CompositeAttributionProvider
import app.core.services.attribution.GooglePlayInstallReferrerAttributionProvider
import app.core.services.billing.AnalyticsBillingClientDecorator
import app.core.services.billing.BillingClient
import app.core.services.billing.GoogleBillingStoreCountryProvider
import app.core.services.billing.PurchasesPreferencesDataStore
import app.core.services.billing.google.BillingClientWrapper
import app.core.services.billing.google.GoogleBillingClient
import app.core.services.billing.google.ObfuscatedUserIdProvider
import app.core.services.billing.model.Purchases
import app.core.services.common.isSystemInDarkTheme
import app.core.services.common.mapOfNotNull
import app.core.services.common.measureExecutionTime
import app.core.services.config.FirebaseRemoteConfig
import app.core.services.config.RemoteConfig
import app.core.services.config.model.RemoteConfigParams.MIN_SUPPORTED_APP_VERSION
import app.core.services.config.model.RemoteConfigValue
import app.core.services.core.model.Attribution
import app.core.services.core.model.ConfigurationResult
import app.core.services.core.model.MediaSource
import app.core.services.data.KeyValueStorageImpl
import app.core.services.data.PreferencesDataStore
import app.core.services.deeplink.DeepLinkManager
import app.core.services.deeplink.af.AppsFlyerDeepLinkManager
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

internal class AppCoreServicesImpl(
    override val billingClient: BillingClient,
    override val deepLinkManager: DeepLinkManager,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val appUpdateManager: AppUpdateManager,
    private val firebaseRemoteConfig: FirebaseRemoteConfig,
    private val amplitudeAnalytics: AmplitudeAnalytics,
    private val attributionServerClient: AttributionServerClient?,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    private val compositeAnalytics: CompositeAnalytics,
    private val configuration: AppCoreServices.Configuration,
    private val preferencesDataStore: PreferencesDataStore,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val attributionProvider: AttributionProvider,
) : AppCoreServices {
    private val applicationScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    override val analytics: Analytics = compositeAnalytics

    override val remoteConfig: RemoteConfig = firebaseRemoteConfig

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
                val isFirstAppLaunch = isFirstLaunch != false
                        && preferencesDataStore.isFirstLaunch()

                if (isFirstAppLaunch) {
                    amplitudeAnalytics.logEvent(AnalyticsEvents.FIRST_LAUNCH)
                    amplitudeAnalytics.flush()
                    amplitudeAnalytics.sendCohort()
                    preferencesDataStore.setFirstLaunch(false)
                }

                amplitudeAnalytics.logEvent(
                    event = AnalyticsEvents.APP_LAUNCH,
                    properties = mapOf("first_launch" to isFirstAppLaunch)
                )

                val attributionDeferred = async {
                    measureExecutionTime("Attribution") {
                        attributionProvider.provide()
                    }
                }

                setupFirebaseAppInstanceId()

                val remoteConfigsDeferred = getRemoteConfigs()
                val purchasesDeferred = getPurchases()
                val storeCountryDeferred = queryStoreCountry()

                val attribution = attributionDeferred.await() ?: Attribution(MediaSource())

                val remoteConfigs = withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                    remoteConfigsDeferred.await()
                }

                val purchases = withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                    purchasesDeferred.await()
                }

                val storeCountry = withTimeoutOrNull(MAX_TIMEOUT_IN_MILLIS) {
                    storeCountryDeferred.await()
                }

                appsFlyerAnalytics.appsFlyerUID?.let {
                    attributionServerClient?.install(it)
                }

                firebaseRemoteConfig.attribution = attribution

                sendTestDistribution(
                    isFirstAppLaunch,
                    storeCountry,
                    attribution,
                    remoteConfigs
                )

                appUpdateManager.setMinSupportedVersionCode(
                    remoteConfig.getLong(MIN_SUPPORTED_APP_VERSION)
                )

                val activePaywallName = remoteConfig.getActivePaywallName()

                configurationResult = ConfigurationResult(
                    activePaywall = activePaywallName,
                    attribution = attribution,
                    purchases = purchases,
                    isFirstLaunch = isFirstAppLaunch
                )

                Timber.d("Finished with $configurationResult.")

                configurationResult!!
            }
        }
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

    private fun sendTestDistribution(
        isFirstAppLaunch: Boolean,
        storeCountry: String?,
        attribution: Attribution,
        remoteConfigs: Map<String, RemoteConfigValue>?
    ) {
        val remoteConfigsProperties = remoteConfigs.orEmpty()
            .filter {
                val value = configuration.remoteConfigParameters.parameters[it.key]
                value?.target != null
            }
            .mapValues {
                val shouldSend = configuration.remoteConfigParameters
                    .parameters[it.key]
                    ?.target?.matches(attribution)
                    ?: false

                val value = it.value.rawValue

                if (!shouldSend || value.isNullOrBlank()) {
                    "none"
                } else if (value.startsWith("none_")) {
                    "none"
                } else {
                    value
                }
            }

        val attributionProperties = mapOfNotNull(
            "network" to attribution.mediaSource.value,
            "campaignName" to attribution.campaign,
            "adGroupName" to attribution.adGroup,
            "ad" to attribution.ad,
            "deep_link_value" to attribution.deepLinkValue
        )

        val eventProperties = attributionProperties + remoteConfigsProperties

        val userProperties = buildMap {
            if (isFirstAppLaunch && attributionProperties.isNotEmpty()) {
                putAll(attributionProperties)
            }

            putAll(remoteConfigsProperties)

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
    }

    private fun getRemoteConfigs(): Deferred<Map<String, RemoteConfigValue>> {
        return applicationScope.async {
            measureExecutionTime("Remote configs") {
                firebaseRemoteConfig.fetchAndActivate()
                val configs = remoteConfig.getAll()

                Timber.d(
                    "Configs: \n%s.",
                    configs
                        .mapValues { it.value.rawValue }
                        .entries
                        .joinToString(",\n") { "${it.key} = ${it.value}" }
                )

                configs
            }
        }
    }

    private fun <T> fetchDataAsync(
        name: String,
        block: suspend () -> T
    ): Deferred<T?> {
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

    private fun getPurchases(): Deferred<Purchases?> {
        return fetchDataAsync(name = "Purchases") {
            billingClient.getPurchases()
        }
    }

    private fun queryStoreCountry(): Deferred<String?> {
        return fetchDataAsync(name = "Store country") {
            billingClient.getStoreCountry()
        }
    }

    internal companion object {
        const val MAX_TIMEOUT_IN_MILLIS = 6_500L

        fun create(configuration: AppCoreServices.Configuration): AppCoreServices {
            val amplitudeAnalytics = AmplitudeAnalytics(
                context = configuration.context,
                apiKey = configuration.amplitudeApiKey,
                sessionReplayConfig = configuration.sessionReplayConfig
            )

            val appsFlyerAnalytics = AppsFlyerAnalytics(
                devKey = configuration.appsFlyerDevKey,
                applicationContext = configuration.context,
            )

            val firebaseAnalytics = FirebaseAnalytics()

            val obfuscatedUserIdProvider = object : ObfuscatedUserIdProvider(
                secretKey = configuration.billingConfig.secretKey,
                iv = configuration.billingConfig.iv,
            ) {
                override fun provideUserId(): String? {
                    return appsFlyerAnalytics.appsFlyerUID
                }
            }

            val billingClient = AnalyticsBillingClientDecorator(
                decorated = GoogleBillingClient(
                    config = configuration.billingConfig,
                    billingClientWrapper = BillingClientWrapper.create(
                        context = configuration.context,
                        obfuscatedUserIdProvider = obfuscatedUserIdProvider,
                        ioDispatcher = Dispatchers.IO,
                        acknowledgePurchases = configuration.billingConfig.acknowledgePurchases
                    ),
                    purchasesDataStore = PurchasesPreferencesDataStore.create(configuration.context)
                ),
                appsFlyerAnalytics = appsFlyerAnalytics,
                firebaseAnalytics = firebaseAnalytics,
                amplitudeAnalytics = amplitudeAnalytics,
            )

            val remoteConfig = FirebaseRemoteConfig(configuration.remoteConfigParameters)

            val preferenceDataStore = PreferenceDataStoreFactory.create {
                configuration.context.preferencesDataStoreFile(configuration.dataStoreFileName)
            }

            val preferencesDataStore = PreferencesDataStore(preferenceDataStore)

            val keyValueStorage = KeyValueStorageImpl(preferenceDataStore)

            val attributionServerClient = if (configuration.attributionServerConfig != null) {
                AttributionServerClient.create(
                    applicationContext = configuration.context,
                    attributionServerConfig = configuration.attributionServerConfig,
                    billingStoreCountryProvider = GoogleBillingStoreCountryProvider(billingClient),
                    keyValueStorage = keyValueStorage
                )
            } else {
                null
            }

            val appsFlyerAttributionProvider = AppsFlyerAttributionProvider(
                analytics = amplitudeAnalytics,
                preferencesDataStore = preferencesDataStore,
                appsFlyerAnalytics = appsFlyerAnalytics,
            )

            val googlePlayInstallReferrerAttributionProvider =
                GooglePlayInstallReferrerAttributionProvider(
                    applicationContext = configuration.context,
                    preferencesDataStore = preferencesDataStore,
                    analytics = amplitudeAnalytics
                )

            return AppCoreServicesImpl(
                firebaseAnalytics = firebaseAnalytics,
                appUpdateManager = AppUpdateManager(configuration.context, remoteConfig),
                firebaseRemoteConfig = remoteConfig,
                amplitudeAnalytics = amplitudeAnalytics,
                appsFlyerAnalytics = appsFlyerAnalytics,
                compositeAnalytics = CompositeAnalytics(
                    analytics = listOf(
                        amplitudeAnalytics,
                        firebaseAnalytics,
                        appsFlyerAnalytics
                    )
                ),
                attributionServerClient = attributionServerClient,
                billingClient = billingClient,
                configuration = configuration,
                preferencesDataStore = preferencesDataStore,
                deepLinkManager = AppsFlyerDeepLinkManager(),
                attributionProvider = CompositeAttributionProvider(
                    timeout = MAX_TIMEOUT_IN_MILLIS,
                    providers = listOf(
                        appsFlyerAttributionProvider,
                        googlePlayInstallReferrerAttributionProvider
                    )
                )
            )
        }
    }
}