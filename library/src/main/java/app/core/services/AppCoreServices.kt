package app.core.services

import android.content.Context
import app.core.services.AppCoreServices.Companion.configure
import app.core.services.analytics.Analytics
import app.core.services.analytics.amplitude.sessionreplay.SessionReplayConfig
import app.core.services.attribution.AttributionServerConfig
import app.core.services.billing.BillingClient
import app.core.services.billing.BillingConfig
import app.core.services.config.RemoteConfig
import app.core.services.config.model.RemoteConfigParameters
import app.core.services.core.AppCoreServicesImpl
import app.core.services.core.model.ConfigurationResult
import app.core.services.deeplink.DeepLinkManager

/**
 * The main entry point for the App Core Services SDK.
 *
 * This interface provides a central access point for all core SDK features, including analytics,
 * remote configuration, and billing management.
 *
 * ### Quickstart Guide:
 * 1. **Configure:** Call `AppCoreServices.configure(your_config)` once during app startup.
 * 2. **Access:** Use `AppCoreServices.sharedInstance` anywhere in your app to get the configured instance.
 */
interface AppCoreServices {
    /**
     * Provides access to the analytics module for tracking events and user properties.
     */
    val analytics: Analytics

    /**
     * Provides access to the remote configuration module for fetching and using remote values.
     */
    val remoteConfig: RemoteConfig

    /**
     * Provides access to the billing module for managing products, purchases, and subscriptions.
     */
    val billingClient: BillingClient

    /**
     * Provides access to the deep link manager for handling and processing deep links.
     */
    val deepLinkManager: DeepLinkManager

    /**
     * Initializes the SDK by fetching all necessary remote data, such as configuration,
     * attribution, and user entitlements.
     *
     * This method should be called once, typically on application launch.
     *
     * @param isFirstLaunch A flag to indicate if this is the first time the app is being launched.
     *                      If `null`, the SDK will determine this automatically.
     * @return A [ConfigurationResult] containing the fetched configuration and user state.
     */
    suspend fun initialize(isFirstLaunch: Boolean? = null): ConfigurationResult

    /**
     * Retrieves the most recently fetched configuration result.
     *
     * @return The cached [ConfigurationResult], or `null` if the SDK has not been initialized yet.
     */
    fun getConfigurationResult(): ConfigurationResult?

    /**
     * Retrieves the unique identifier for the current user.
     *
     * @return The user ID as a String, or `null` if the user has not been identified yet.
     */
    fun getUserId(): String?

    /**
     * Associates a custom user ID with the current user.
     *
     * This method is used to link the SDK's internal user identifier with your own
     * system's user ID. This is crucial for cross-referencing user data between your backend
     * and the analytics/attribution services integrated with the SDK.
     *
     * For example, if a user logs into your app, you should call this method with their
     * unique ID from your database.
     *
     * @param externalUserId The custom user ID to associate with the current user.
     */
    fun setExternalUserId(externalUserId: String?)

    class Configuration(
        val context: Context,
        val appsFlyerDevKey: String,
        val amplitudeApiKey: String,
        val billingConfig: BillingConfig,
        val remoteConfigParameters: RemoteConfigParameters,
        val attributionServerConfig: AttributionServerConfig? = null,
        val sessionReplayConfig: SessionReplayConfig = SessionReplayConfig(),
        val dataStoreFileName: String,
    )

    companion object {
        @Volatile
        private var INSTANCE: AppCoreServices? = null

        /**
         * The globally accessible singleton instance of the App Core Services.
         *
         * @return A previously configured singleton [AppCoreServices] instance.
         * @throws UninitializedPropertyAccessException if [configure] has not been called before accessing this property.
         */
        @JvmStatic
        val sharedInstance: AppCoreServices
            get() {
                return INSTANCE
                    ?: throw UninitializedPropertyAccessException("SDK not configured. Call AppCoreServices.configure() first.")
            }

        /**
         * Configures and initializes the singleton instance of the App Core Services.
         *
         * This method must be called once, typically in your `Application.onCreate()` method,
         * before any other SDK functionality is used. Subsequent calls will be ignored.
         *
         * @param configuration The [Configuration] object containing all necessary settings.
         * @return The configured and ready-to-use singleton instance of [AppCoreServices].
         */
        @JvmStatic
        fun configure(configuration: Configuration): AppCoreServices {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppCoreServicesImpl.create(configuration).also { INSTANCE = it }
            }
        }
    }
}