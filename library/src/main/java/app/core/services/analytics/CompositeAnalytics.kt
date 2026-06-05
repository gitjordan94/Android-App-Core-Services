package app.core.services.analytics

import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.amplitude.analytics.AmplitudeAnalytics
import app.core.services.billing.model.Purchase

/**
 * A composite analytics implementation that delegates to multiple analytics providers.
 */
class CompositeAnalytics(
    private val analytics: List<Analytics>
) : Analytics, PurchaseEventLogger {
    override fun setUserProperties(properties: Map<String, Any?>?) {
        analytics.forEach { it.setUserProperties(properties) }
    }

    override fun logEvent(event: String, properties: Map<String, Any?>?) {
        analytics
            .filterNot { it is AppsFlyerAnalytics }
            .forEach { it.logEvent(event, properties) }
    }

    override fun logEvent(event: AnalyticsEvent) {
        logEvent(event.type, event.properties)
    }

    override fun logPurchase(purchase: Purchase) {
        for (analytics in analytics) {
            if (analytics is PurchaseEventLogger) {
                analytics.logPurchase(purchase)
            }
        }
    }

    override fun enableSessionReplay() {
        analytics.firstOrNull { it is AmplitudeAnalytics }
            ?.enableSessionReplay()
    }

    override fun disableSessionReplay() {
        analytics.firstOrNull { it is AmplitudeAnalytics }
            ?.disableSessionReplay()
    }
}