package app.core.services.amplitude

data class AmplitudeConfig(
    val apiKey: String,
    val optOut: Boolean = false,
)