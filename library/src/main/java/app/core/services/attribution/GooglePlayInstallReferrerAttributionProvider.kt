package app.core.services.attribution

import android.content.Context
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvents
import app.core.services.attribution.installreferrer.GoogleInstallReferrerProvider
import app.core.services.attribution.installreferrer.InstallReferrerProvider
import app.core.services.core.model.Attribution
import app.core.services.data.PreferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class GooglePlayInstallReferrerAttributionProvider(
    private val applicationContext: Context,
    private val analytics: Analytics,
    private val preferencesDataStore: PreferencesDataStore,
    private val installReferrerProvider: InstallReferrerProvider = GoogleInstallReferrerProvider(
        applicationContext
    ),
    private val attributionParser: AttributionParser<String?> = GooglePlayInstallReferrerAttributionParser(),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AttributionProvider {
    override suspend fun provide(): Attribution {
        return withContext(coroutineDispatcher) {
            var installReferrer: String? = preferencesDataStore.getInstallReferrer()

            Timber.d("Preferences Google Play Install Referrer: $installReferrer")

            if (installReferrer == null) {
                val referrerDetails = installReferrerProvider.getInstallReferrer()
                installReferrer = referrerDetails?.installReferrer

                if (installReferrer != null) {
                    preferencesDataStore.setInstallReferrer(installReferrer)
                }
            }

            val attribution = attributionParser.parse(installReferrer)

            val eventProperties = mutableMapOf<String, Any?>("referrer" to installReferrer)
            if (!attribution.rawData.isNullOrEmpty()) {
                eventProperties.putAll(attribution.rawData)
            }

            analytics.logEvent(
                event = AnalyticsEvents.INSTALL_REFERER,
                properties = eventProperties
            )

            Timber.d("Google Play Install Referrer attribution data: $attribution")

            attribution
        }
    }
}