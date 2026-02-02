package app.core.services.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.core.services.AppCoreServices
import app.core.services.analytics.CompositeAnalytics
import app.core.services.analytics.amplitude.AmplitudeAnalytics
import app.core.services.analytics.firebase.FirebaseAnalytics
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.appsflyer.attribution.AppsFlyerAttributionProvider
import app.core.services.appupdates.AppUpdateManager
import app.core.services.attribution.AppsflyerDeviceIdProvider
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
import app.core.services.config.FirebaseRemoteConfig
import app.core.services.core.DefaultAppCoreServices.Companion.MAX_TIMEOUT_IN_MILLIS
import app.core.services.data.KeyValueStorageImpl
import app.core.services.data.PreferencesDataStore
import app.core.services.deeplink.af.AppsFlyerDeepLinkManager
import app.core.services.deviceinfo.DeviceInfoProviderFactory

internal object AppCoreServiceProvider {
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

        val billingClient = createBilling(
            configuration,
            appsFlyerAnalytics,
            firebaseAnalytics,
            amplitudeAnalytics
        )

        val remoteConfig = FirebaseRemoteConfig(configuration.remoteConfigParameters)

        val preferenceDataStore = PreferenceDataStoreFactory.create {
            configuration.context.preferencesDataStoreFile(configuration.dataStoreFileName)
        }

        val preferencesDataStore = PreferencesDataStore(preferenceDataStore)

        val keyValueStorage = KeyValueStorageImpl(preferenceDataStore)

        val deviceIdProvider = AppsflyerDeviceIdProvider(appsFlyerAnalytics)

        val attributionServerClient = if (configuration.attributionServerConfig != null) {
            AttributionServerClient.create(
                applicationContext = configuration.context,
                attributionServerConfig = configuration.attributionServerConfig,
                billingStoreCountryProvider = GoogleBillingStoreCountryProvider(billingClient),
                keyValueStorage = keyValueStorage,
                deviceIdProvider = deviceIdProvider
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

        return DefaultAppCoreServices(
            configuration = configuration,
            // Attribution
            attributionServerClient = attributionServerClient,
            attributionProvider = CompositeAttributionProvider(
                timeout = MAX_TIMEOUT_IN_MILLIS,
                providers = listOf(
                    appsFlyerAttributionProvider,
                    googlePlayInstallReferrerAttributionProvider
                )
            ),
            // Analytics
            amplitudeAnalytics = amplitudeAnalytics,
            firebaseAnalytics = firebaseAnalytics,
            appsFlyerAnalytics = appsFlyerAnalytics,
            compositeAnalytics = CompositeAnalytics(
                analytics = listOf(
                    amplitudeAnalytics,
                    firebaseAnalytics,
                    appsFlyerAnalytics
                )
            ),
            appUpdateManager = AppUpdateManager(configuration.context, remoteConfig),
            firebaseRemoteConfig = remoteConfig,
            billingClient = billingClient,
            preferencesDataStore = preferencesDataStore,
            deepLinkManager = AppsFlyerDeepLinkManager(),
            deviceInfoProvider = DeviceInfoProviderFactory.create(configuration.context),
            deviceIdProvider = deviceIdProvider
        )
    }

    private fun createBilling(
        configuration: AppCoreServices.Configuration,
        appsFlyerAnalytics: AppsFlyerAnalytics,
        firebaseAnalytics: FirebaseAnalytics,
        amplitudeAnalytics: AmplitudeAnalytics,
    ): BillingClient {
        val obfuscatedUserIdProvider = object : ObfuscatedUserIdProvider(
            secretKey = configuration.billingConfig.secretKey,
            iv = configuration.billingConfig.iv,
        ) {
            override fun provideUserId(): String? {
                return appsFlyerAnalytics.appsFlyerUID
            }
        }

        return AnalyticsBillingClientDecorator(
            decorated = GoogleBillingClient(
                config = configuration.billingConfig,
                billingClientWrapper = BillingClientWrapper.create(
                    context = configuration.context,
                    obfuscatedUserIdProvider = obfuscatedUserIdProvider,
                    acknowledgePurchases = configuration.billingConfig.acknowledgePurchases
                ),
                purchasesDataStore = PurchasesPreferencesDataStore.create(configuration.context)
            ),
            appsFlyerAnalytics = appsFlyerAnalytics,
            firebaseAnalytics = firebaseAnalytics,
            amplitudeAnalytics = amplitudeAnalytics,
        )
    }
}