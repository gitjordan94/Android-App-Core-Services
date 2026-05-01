package app.core.services.testing.config

import app.core.services.config.model.RemoteConfigValue
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber
import java.util.regex.Pattern

internal class FakeRemoteConfigValue(
    override val value: String?,
    override val payloadJson: String? = null,
) : RemoteConfigValue {
    override fun asLong(): Long? {
        val valueAsString = asTrimmedString() ?: return null

        return try {
            valueAsString.toLong()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                String.format(
                    ILLEGAL_ARGUMENT_STRING_FORMAT,
                    valueAsString,
                    "long"
                ), e
            )
        }
    }

    override fun asBoolean(): Boolean? {
        val valueAsString = asTrimmedString() ?: return null

        return if (TRUE_REGEX.matcher(valueAsString).matches()) {
            true
        } else if (FALSE_REGEX.matcher(valueAsString).matches()) {
            false
        } else {
            throw IllegalArgumentException(
                String.format(
                    ILLEGAL_ARGUMENT_STRING_FORMAT,
                    valueAsString,
                    "boolean"
                )
            )
        }
    }

    /** Returns a trimmed version of [.asString].  */
    private fun asTrimmedString(): String? {
        return value?.trim { it <= ' ' }
    }

    override fun payload(): Any? {
        val payloadJson = payloadJson?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            JSONTokener(payloadJson).nextValue()
        } catch (e: Throwable) {
            Timber.e(e, "Failed to parse payload.")
            payloadJson
        }
    }

    override fun payloadAsInt(): Int? {
        return when (val payload = payload() ?: return null) {
            is Number -> payload.toInt()
            is Boolean -> if (payload) 1 else 0
            is String -> payload.trim().toIntOrNull()
            else -> throw illegalPayloadConversion("int")
        }
    }

    override fun payloadAsLong(): Long? {
        return when (val payload = payload() ?: return null) {
            is Number -> payload.toLong()
            is Boolean -> if (payload) 1L else 0L
            is String -> payload.trim().toLongOrNull()
            else -> throw illegalPayloadConversion("long")
        }
    }

    override fun payloadAsBoolean(): Boolean? {
        return when (val payload = payload() ?: return null) {
            is Boolean -> payload
            is Number -> payload.toLong() != 0L
            is String -> {
                val s = payload.trim()
                when {
                    TRUE_REGEX.matcher(s).matches() -> true
                    FALSE_REGEX.matcher(s).matches() -> false
                    else -> throw illegalPayloadConversion("boolean")
                }
            }

            else -> throw illegalPayloadConversion("boolean")
        }
    }

    override fun payloadAsString(): String? {
        return when (val payload = payload() ?: return null) {
            is String -> payload
            is Number, is Boolean -> payload.toString()
            is JSONObject, is JSONArray -> payload.toString()
            else -> null
        }
    }

    override fun payloadAsJsonObject(): JSONObject? {
        return when (val payload = payload() ?: return null) {
            is JSONObject -> payload
            is String -> runCatching { JSONObject(payload) }
                .onFailure { e -> Timber.e(e, "Failed to parse payload.") }
                .getOrNull()
                ?: throw illegalPayloadConversion("JSONObject")

            else -> null
        }
    }

    override fun payloadAsJsonArray(): JSONArray? {
        return when (val payload = payload() ?: return null) {
            is JSONArray -> payload
            is String -> runCatching { JSONArray(payload) }
                .onFailure { e -> Timber.e(e, "Failed to parse payload.") }
                .getOrNull()
                ?: throw illegalPayloadConversion("JSONArray")

            else -> null
        }
    }

    private fun illegalPayloadConversion(target: String): IllegalArgumentException {
        return IllegalArgumentException(
            String.format(
                "[Payload: %s] cannot be converted to a %s.",
                payloadJson,
                target
            )
        )
    }

    private companion object {
        private const val ILLEGAL_ARGUMENT_STRING_FORMAT =
            "[Value: %s] cannot be converted to a %s."

        private val TRUE_REGEX: Pattern =
            Pattern.compile("^(1|true|t|yes|y|on)$", Pattern.CASE_INSENSITIVE)

        private val FALSE_REGEX: Pattern =
            Pattern.compile("^(0|false|f|no|n|off|none)$", Pattern.CASE_INSENSITIVE)
    }
}