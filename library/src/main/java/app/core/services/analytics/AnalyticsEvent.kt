package app.core.services.analytics

interface AnalyticsEvent {
    val type: String
    val properties: Map<String, Any?>?
}