package app.core.services.config.model

import app.core.services.config.ExperimentVariant
import app.core.services.config.RemoteConfigMatchingContext
import com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT
import com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import timber.log.Timber
import java.util.regex.Pattern

internal class RemoteConfigValueImpl internal constructor(
    val key: String,
    override val rawValue: String?,
    override val source: RemoteConfigSource,
    private val value: String,
) : RemoteConfigValue {
    override fun asLong(): Long {
        if (source == RemoteConfigSource.STATIC) {
            return 0L
        }

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
        if (source == RemoteConfigSource.STATIC) {
            return 0.0
        }

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
        if (source == RemoteConfigSource.STATIC) {
            return ""
        }

        return value
    }

    override fun asByteArray(): ByteArray {
        if (source == RemoteConfigSource.STATIC) {
            return ByteArray(0)
        }

        val value = asString()

        if (value.isEmpty()) {
            return ByteArray(0)
        }

        return value.toByteArray()
    }

    override fun asBoolean(): Boolean {
        if (source == RemoteConfigSource.STATIC) {
            return false
        }

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

    /** Returns a trimmed version of [.asString].  */
    private fun asTrimmedString(): String {
        return asString().trim { it <= ' ' }
    }

    internal companion object {
        private const val ILLEGAL_ARGUMENT_STRING_FORMAT =
            "[Value: %s] cannot be converted to a %s."

        private val TRUE_REGEX: Pattern =
            Pattern.compile("^(1|true|t|yes|y|on)$", Pattern.CASE_INSENSITIVE)

        private val FALSE_REGEX: Pattern =
            Pattern.compile("^(0|false|f|no|n|off|none)$", Pattern.CASE_INSENSITIVE)

        internal fun from(
            key: String,
            value: FirebaseRemoteConfigValue,
            context: RemoteConfigMatchingContext?,
            default: RemoteConfigParameter?
        ): RemoteConfigValue {
            val rawValue = try {
                val remoteValue = value.asString()
                remoteValue
            } catch (e: Throwable) {
                Timber.e(e)
                null
            }

            return from(
                key = key,
                value = rawValue.orEmpty(),
                source = when (value.source) {
                    VALUE_SOURCE_DEFAULT -> RemoteConfigSource.DEFAULT
                    VALUE_SOURCE_REMOTE -> RemoteConfigSource.REMOTE
                    else -> RemoteConfigSource.STATIC
                },
                context = context,
                default = default
            )
        }

        private fun from(
            key: String,
            value: String,
            source: RemoteConfigSource,
            context: RemoteConfigMatchingContext?,
            default: RemoteConfigParameter?
        ): RemoteConfigValue {
            val rawValue = default?.getValue(value, context) ?: value

            val resolvedValue = when {
                rawValue == ExperimentVariant.NONE -> ""
                rawValue.startsWith(ExperimentVariant.NONE_PREFIX) -> {
                    rawValue.removePrefix(ExperimentVariant.NONE_PREFIX)
                }

                else -> rawValue
            }

            return RemoteConfigValueImpl(
                key = key,
                rawValue = rawValue,
                source = source,
                value = resolvedValue
            )
        }
    }
}