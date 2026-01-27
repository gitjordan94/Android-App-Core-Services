package app.core.services.amplitude.experiment

internal data class ExperimentVariant(
    val key: String,
    val value: String?,
    val payloadJson: String?,
)