package app.core.services.analytics

interface EventLogger {
    fun logEvent(event: String, properties: Map<String, Any?>? = null)

    fun logEvent(event: AnalyticsEvent)
}