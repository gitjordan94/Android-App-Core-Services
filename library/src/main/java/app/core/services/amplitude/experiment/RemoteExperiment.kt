package app.core.services.amplitude.experiment

internal interface RemoteExperiment {
    suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    ): Boolean

    operator fun get(key: String): ExperimentVariant

    fun exposure(key: String)
}