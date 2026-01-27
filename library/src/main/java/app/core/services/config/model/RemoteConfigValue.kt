package app.core.services.config.model

import org.json.JSONArray
import org.json.JSONObject

interface RemoteConfigValue {
    val value: String?

    val payloadJson: String?

    @Throws(IllegalArgumentException::class)
    fun asLong(): Long?

    @Throws(IllegalArgumentException::class)
    fun asBoolean(): Boolean?

    @Throws(IllegalArgumentException::class)
    fun payload(): Any?

    @Throws(IllegalArgumentException::class)
    fun payloadAsInt(): Int?

    @Throws(IllegalArgumentException::class)
    fun payloadAsLong(): Long?

    @Throws(IllegalArgumentException::class)
    fun payloadAsBoolean(): Boolean?

    @Throws(IllegalArgumentException::class)
    fun payloadAsString(): String?

    @Throws(IllegalArgumentException::class)
    fun payloadAsJsonObject(): JSONObject?

    @Throws(IllegalArgumentException::class)
    fun payloadAsJsonArray(): JSONArray?
}