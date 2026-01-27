package app.core.services.config

import app.core.services.config.model.RemoteConfigValue

interface RemoteConfig {
    suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    ): Boolean

    operator fun get(key: String): RemoteConfigValue

    fun getLong(key: String): Long?

    fun getBoolean(key: String): Boolean?

    fun getString(key: String): String?

    fun getPayload(key: String): Any?
}