package app.core.services.config

internal interface InternalRemoteConfig : RemoteConfig {
    suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    ): Boolean
}