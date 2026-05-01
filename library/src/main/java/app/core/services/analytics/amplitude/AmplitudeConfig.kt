package app.core.services.analytics.amplitude

data class AmplitudeConfig(
    val apiKey: String,
    val optOut: Boolean = false,
)