package app.core.services.testing.config

import app.core.services.config.RemoteConfig
import app.core.services.config.model.RemoteConfigValue

class FakeRemoteConfig(
    values: Map<String, String> = emptyMap(),
) : RemoteConfig {
    private val values = values.toMutableMap()

    fun set(key: String, value: String) {
        values[key] = value
    }

    fun set(key: String, value: Boolean) = set(key, value.toString())

    fun set(key: String, value: Long) = set(key, value.toString())

    fun set(key: String, value: Double) = set(key, value.toString())

    override suspend fun fetch(userId: String?, userProperties: Map<String, Any?>?) = true

    override fun get(key: String): RemoteConfigValue {
        return FakeRemoteConfigValue(values[key])
    }

    override fun getLong(key: String): Long? {
        return get(key).asLong()
    }

    override fun getBoolean(key: String): Boolean? {
        return get(key).asBoolean()
    }

    override fun getString(key: String): String? {
        return get(key).value
    }

    override fun getPayload(key: String): Any? {
        return get(key).payload()
    }
}