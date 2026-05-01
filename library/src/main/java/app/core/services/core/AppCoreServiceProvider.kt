package app.core.services.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.core.services.AppCoreServices
import app.core.services.analytics.CompositeAnalytics
import app.core.services.analytics.amplitude.AmplitudeAnalytics
import app.core.services.analytics.firebase.FirebaseAnalytics
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.appsflyer.AppsFlyerUidProvider
import app.core.services.appsflyer.DefaultAppsFlyerUidProvider
import app.core.services.appsflyer.attribution.AppsFlyerAttributionProvider
import app.core.services.appupdates.GoogleInAppUpdateManager
import app.core.services.attribution.AdvertisingIdProvider
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
import app.core.services.core.DefaultAppCoreServices.Companion.MAX_TIMEOUT_MS
import app.core.services.core.appsetid.AndroidAppSetIdProvider
import app.core.services.data.KeyValueStorageImpl
import app.core.services.data.PreferencesDataStore
import app.core.services.deeplink.af.AppsFlyerDeepLinkManager
import app.core.services.deviceinfo.DeviceInfoProviderFactory
import com.appsflyer.AppsFlyerLib

internal object AppCoreServiceProvider {
    fun create(configuration: AppCoreServices.Configuration): AppCoreServices {
        val firebaseAnalytics = FirebaseAnalytics()

        val amplitudeAnalytics = AmplitudeAnalytics(
            context = configuration.context,
            amplitudeConfig = configuration.amplitudeConfig,
            sessionReplayConfig = configuration.sessionReplayConfig
        )

        val appsFlyer = AppsFlyerLib.getInstance()

        val appsFlyerUidProvider = DefaultAppsFlyerUidProvider(
            appsFlyer = appsFlyer,
            applicationContext = configuration.context
        )

        val appsFlyerAnalytics = AppsFlyerAnalytics(
            devKey = configuration.appsFlyerDevKey,
            applicationContext = configuration.context,
            appsFlyer = appsFlyer,
        )

        val billingClient = createBilling(
            configuration,
            appsFlyerAnalytics,
            firebaseAnalytics,
            amplitudeAnalytics,
            appsFlyerUidProvider
        )

        val firebaseRemoteConfig = FirebaseRemoteConfig(configuration.remoteConfigParameters)

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
                timeout = MAX_TIMEOUT_MS,
                providers = listOf(
                    appsFlyerAttributionProvider,
                    googlePlayInstallReferrerAttributionProvider
                )
            ),
            // Analytics
            amplitudeAnalytics = amplitudeAnalytics,
            firebaseAnalytics = firebaseAnalytics,
            appsFlyerAnalytics = appsFlyerAnalytics,
            appUpdateManager = GoogleInAppUpdateManager(
                configuration.context,
                firebaseRemoteConfig
            ),
            remoteConfig = firebaseRemoteConfig,
            billingClient = billingClient,
            preferencesDataStore = preferencesDataStore,
            deepLinkManager = AppsFlyerDeepLinkManager(),
            deviceInfoProvider = DeviceInfoProviderFactory.create(configuration.context),
            deviceIdProvider = deviceIdProvider,
            appSetIdProvider = AndroidAppSetIdProvider(configuration.context),
            advertisingIdProvider = AdvertisingIdProvider.create(configuration.context),
            analytics = CompositeAnalytics(
                analytics = listOf(
                    amplitudeAnalytics,
                    firebaseAnalytics,
                    appsFlyerAnalytics
                )
            )
        )
    }

    private fun createBilling(
        configuration: AppCoreServices.Configuration,
        appsFlyerAnalytics: AppsFlyerAnalytics,
        firebaseAnalytics: FirebaseAnalytics,
        amplitudeAnalytics: AmplitudeAnalytics,
        appsFlyerUidProvider: AppsFlyerUidProvider,
    ): BillingClient {
        val obfuscatedUserIdProvider = ObfuscatedUserIdProvider(
            secretKey = configuration.billingConfig.secretKey,
            iv = configuration.billingConfig.iv,
            appsFlyerUidProvider = appsFlyerUidProvider
        )

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