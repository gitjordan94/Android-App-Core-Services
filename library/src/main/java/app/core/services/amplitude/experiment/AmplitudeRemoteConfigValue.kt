package app.core.services.amplitude.experiment

import app.core.services.config.model.RemoteConfigValue
import java.util.regex.Pattern

internal class AmplitudeRemoteConfigValue(
    private val variant: ExperimentVariant,
) : RemoteConfigValue {
    override val value: String?
        get() = variant.value

    override val payload: Any?
        get() = variant.payload

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
                ),
                e
            )
        }
    }

    override fun asString(): String? {
        return variant.value
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

    private companion object {
        private const val ILLEGAL_ARGUMENT_STRING_FORMAT =
            "[Value: %s] cannot be converted to a %s."

        private val TRUE_REGEX: Pattern =
            Pattern.compile("^(1|true|t|yes|y|on)$", Pattern.CASE_INSENSITIVE)

        private val FALSE_REGEX: Pattern =
            Pattern.compile("^(0|false|f|no|n|off|none)$", Pattern.CASE_INSENSITIVE)
    }
}