package app.core.services.appsflyer.attribution

import app.core.services.analytics.Analytics
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.attribution.AttributionParser
import app.core.services.attribution.AttributionProvider
import app.core.services.core.model.Attribution
import app.core.services.data.PreferencesDataStore
import kotlinx.coroutines.TimeoutCancellationException
import timber.log.Timber

internal class AppsFlyerAttributionProvider(
    private val analytics: Analytics,
    private val preferencesDataStore: PreferencesDataStore,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    private val appsFlyerAttributionParser: AttributionParser<Map<String, Any?>> = AppsFlyerAttributionParser(),
) : AttributionProvider {
    override suspend fun provide(): Attribution? {
        return try {
            var attributionData = preferencesDataStore.getAttributionData()

            Timber.d("Preferences AppsFlyer attribution: $attributionData")

            if (attributionData == null) {
                val conversionData = appsFlyerAnalytics.awaitConversionData()

                analytics.logEvent(
                    event = "AF_CONVERSION_DATA",
                    properties = conversionData
                )

                if (!conversionData.isNullOrEmpty()) {
                    attributionData = appsFlyerAttributionParser.parse(conversionData)
                    preferencesDataStore.setAttributionData(attributionData)
                }

                Timber.d("AppsFlyer attribution data: $attributionData")
            }

            attributionData
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Timeout while getting AppsFlyer attribution")
            null
        } catch (e: Throwable) {
            Timber.e(e, "Error while getting AppsFlyer attribution")
            null
        }
    }
}