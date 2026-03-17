package app.core.services.analytics

import timber.log.Timber

object NoOpAnalytics : Analytics {
    override fun setUserProperties(properties: Map<String, Any?>?) {
        Timber.d("User properties: $properties")
    }

    override fun logEvent(event: String, properties: Map<String, Any?>?) {
        Timber.d("Analytics event: $event, properties: $properties")
    }

    override fun logEvent(event: AnalyticsEvent) {
        logEvent(event.type, event.properties)
    }
}