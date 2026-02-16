package app.core.services.appsflyer.attribution

import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.appsflyer.ConversionDataResult
import app.core.services.attribution.AttributionParser
import app.core.services.attribution.AttributionProvider
import app.core.services.core.model.Attribution
import app.core.services.data.PreferencesDataStore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

internal class AppsFlyerAttributionProvider(
    private val preferencesDataStore: PreferencesDataStore,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    private val appsFlyerAttributionParser: AttributionParser<Map<String, Any?>> = AppsFlyerAttributionParser(),
) : AttributionProvider {
    override suspend fun provide(): Attribution? {
        return try {
            var attributionData = preferencesDataStore.getAttributionData()

            Timber.d("Preferences AppsFlyer attribution: $attributionData")

            if (attributionData == null) {
                val conversionData = appsFlyerAnalytics.conversionDataFlow
                    .filterNotNull()
                    .firstOrNull()

                Timber.d("AppsFlyer conversion data: $conversionData")

                if (conversionData is ConversionDataResult.Success && !conversionData.data.isNullOrEmpty()) {
                    attributionData = appsFlyerAttributionParser.parse(conversionData.data)
                    preferencesDataStore.setAttributionData(attributionData)
                }

                Timber.d("AppsFlyer attribution data: $attributionData")
            } else {
                Timber.d("AppsFlyer attribution data from preferences: $attributionData")
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