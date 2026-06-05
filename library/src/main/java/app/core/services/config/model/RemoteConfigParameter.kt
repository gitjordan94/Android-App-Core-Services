package app.core.services.config.model

import app.core.services.config.ExperimentVariant
import app.core.services.config.RemoteConfigMatchingContext
import app.core.services.config.RemoteConfigTarget

internal class RemoteConfigParameter(
    val key: String,
    rawValue: Any?,
    val target: RemoteConfigTarget? = null,
) {
    val value: String = when {
        target == null -> rawValue?.toString().orEmpty()
        rawValue != null -> "${ExperimentVariant.NONE_PREFIX}$rawValue"
        else -> ExperimentVariant.NONE
    }

    fun getValue(remoteValue: String, data: RemoteConfigMatchingContext?): String {
        return if (target == null || data == null || target.matches(data)) {
            remoteValue
        } else {
            value
        }
    }
}