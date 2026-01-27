package app.core.services.amplitude.experiment

internal interface RemoteExperiment {
    suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    )

    fun getVariant(key: String, defaultValue: ExperimentVariant? = null): ExperimentVariant

    fun exposure(key: String)
}