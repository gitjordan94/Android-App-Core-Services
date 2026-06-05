package app.core.services.config

import app.core.services.config.model.RemoteConfigValue

interface RemoteConfig {
    suspend fun fetch()

    operator fun get(key: String): RemoteConfigValue

    fun getLong(key: String): Long

    fun getDouble(key: String): Double

    fun getBoolean(key: String): Boolean

    fun getString(key: String): String

    fun getAll(): Map<String, RemoteConfigValue>

    fun getActivePaywallName(): String

    fun isSubscriptionStyleFull(): Boolean

    fun isSubscriptionStyleHard(): Boolean

    fun rateUsPrimaryShow(): Boolean

    fun rateUsSecondaryShow(): Boolean

    fun setOnOnConfigUpdateListener(onConfigUpdateListener: OnConfigUpdateListener)
}