package app.core.services.testing.config

import app.core.services.config.OnConfigUpdateListener
import app.core.services.config.RemoteConfig
import app.core.services.config.model.RemoteConfigValue

class FakeRemoteConfig(
    values: Map<String, String> = emptyMap(),
) : RemoteConfig {
    private val values = values.toMutableMap()

    var activePaywall: String? = null
    var isSubscriptionStyleFullValue: Boolean = true
    var isSubscriptionStyleHardValue: Boolean = false
    var rateUsPrimaryShowValue: Boolean = false
    var rateUsSecondaryShowValue: Boolean = false

    fun set(key: String, value: String) {
        values[key] = value
    }

    fun set(key: String, value: Boolean) = set(key, value.toString())

    fun set(key: String, value: Long) = set(key, value.toString())

    fun set(key: String, value: Double) = set(key, value.toString())

    override suspend fun fetch() {
        // No-op
    }

    override fun get(key: String): RemoteConfigValue {
        return FakeRemoteConfigValue(values[key])
    }

    override fun getLong(key: String): Long {
        return get(key).asLong()
    }

    override fun getDouble(key: String): Double {
        return get(key).asDouble()
    }

    override fun getBoolean(key: String): Boolean {
        return get(key).asBoolean()
    }

    override fun getString(key: String): String {
        return get(key).asString()
    }

    override fun getAll(): Map<String, RemoteConfigValue> {
        return values.mapValues { FakeRemoteConfigValue(it.value) }
    }

    override fun getActivePaywallName(): String {
        return activePaywall ?: ""
    }

    override fun isSubscriptionStyleFull(): Boolean {
        return isSubscriptionStyleFullValue
    }

    override fun isSubscriptionStyleHard(): Boolean {
        return isSubscriptionStyleHardValue
    }

    override fun rateUsPrimaryShow(): Boolean {
        return rateUsPrimaryShowValue
    }

    override fun rateUsSecondaryShow(): Boolean {
        return rateUsSecondaryShowValue
    }

    override fun setOnOnConfigUpdateListener(onConfigUpdateListener: OnConfigUpdateListener) {
        // No-op
    }
}