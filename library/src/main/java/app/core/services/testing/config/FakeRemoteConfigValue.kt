package app.core.services.testing.config

import app.core.services.config.model.RemoteConfigSource
import app.core.services.config.model.RemoteConfigValue
import java.util.regex.Pattern

internal class FakeRemoteConfigValue(private val value: String?) : RemoteConfigValue {
    override val rawValue: String?
        get() = value

    override val source: RemoteConfigSource
        get() = RemoteConfigSource.STATIC

    override fun asLong(): Long {
        val valueAsString = asTrimmedString()

        if (valueAsString.isEmpty()) {
            return 0L
        }

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

    override fun asDouble(): Double {
        val valueAsString = asTrimmedString()

        if (valueAsString.isEmpty()) {
            return 0.0
        }

        return try {
            valueAsString.toDouble()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                String.format(
                    ILLEGAL_ARGUMENT_STRING_FORMAT,
                    valueAsString,
                    "double"
                ), e
            )
        }
    }

    override fun asString(): String {
        return value.orEmpty()
    }

    override fun asByteArray(): ByteArray {
        val value = asString()

        if (value.isEmpty()) {
            return ByteArray(0)
        }

        return asString().toByteArray()
    }

    override fun asBoolean(): Boolean {
        val valueAsString = asTrimmedString()

        if (valueAsString.isEmpty()) {
            return false
        }

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

    private fun asTrimmedString(): String {
        return asString().trim { it <= ' ' }
    }

    companion object {
        const val ILLEGAL_ARGUMENT_STRING_FORMAT = "[Value: %s] cannot be converted to a %s."

        val TRUE_REGEX: Pattern = Pattern.compile("^(1|true|t|yes|y|on)$", Pattern.CASE_INSENSITIVE)

        val FALSE_REGEX: Pattern =
            Pattern.compile("^(0|false|f|no|n|off|none)$", Pattern.CASE_INSENSITIVE)
    }
}