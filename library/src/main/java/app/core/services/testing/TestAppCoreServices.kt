package app.core.services.testing

import android.content.Context
import app.core.services.AppCoreServices
import app.core.services.analytics.Analytics
import app.core.services.analytics.NoOpAnalytics
import app.core.services.billing.BillingClient
import app.core.services.config.FirebaseRemoteConfig
import app.core.services.config.RemoteConfig
import app.core.services.config.RemoteConfigMatchingContext
import app.core.services.config.model.RemoteConfigParameters
import app.core.services.consent.Consent
import app.core.services.core.model.Attribution
import app.core.services.core.model.ConfigurationResult
import app.core.services.deeplink.DeepLinkManager
import app.core.services.deeplink.NoOpDeepLinkManager
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID

class TestAppCoreServices(
    override val analytics: Analytics = NoOpAnalytics,
    override val remoteConfig: RemoteConfig = FirebaseRemoteConfig(RemoteConfigParameters()),
    override val billingClient: BillingClient,
    override val deepLinkManager: DeepLinkManager = NoOpDeepLinkManager,
) : AppCoreServices {
    @JvmField
    var configurationResult: ConfigurationResult? = null

    private val _userId by lazy { UUID.randomUUID().toString() + "R" }

    override fun start(context: Context) {
        // No-op
    }

    override fun setConsent(consent: Consent) {
        // No-op
    }

    override suspend fun bootstrap(isFirstLaunch: Boolean?): ConfigurationResult {
        Timber.d("TestAppCoreServices.initialize()")

        val storeCountry = billingClient.getStoreCountry()

        try {
            withTimeout(3_000) {
                remoteConfig.fetch()
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to fetch remote config")
        }

        val attribution = configurationResult?.attribution
            ?: Attribution()

        val remoteConfigMatchingContext = RemoteConfigMatchingContext(
            attribution,
            storeCountry
        )

        if (remoteConfig is FirebaseRemoteConfig) {
            remoteConfig.remoteConfigMatchingContext = remoteConfigMatchingContext
        }

        val configs = remoteConfig.getAll()
            .mapValues { it.value.asString() }

        Timber.d("Remote config: $configs")

        return configurationResult ?: ConfigurationResult(
            activePaywall = remoteConfig.getActivePaywallName(),
            attribution = attribution,
            storeCountry = storeCountry,
            purchases = billingClient.getPurchases(),
            isFirstLaunch = isFirstLaunch ?: true
        ).also { configurationResult = it }
    }

    override fun getConfigurationResult(): ConfigurationResult? = configurationResult

    override fun getUserId(): String = _userId

    override fun setExternalUserId(externalUserId: String?) {
        // No-op
    }
}