package app.core.services.config.model

import app.core.services.config.RemoteConfigMatchingContext
import app.core.services.config.RemoteConfigTarget

internal class RemoteConfigParameter(
    val key: String,
    val defaultValue: String,
    val target: RemoteConfigTarget? = null,
) {
    fun getValue(remoteValue: String, data: RemoteConfigMatchingContext?): String {
        val value = if (target == null || data == null || target.matches(data)) {
            remoteValue
        } else {
            defaultValue
        }

        if (value == "none") {
            return ""
        }

        if (value.startsWith("none_")) {
            return value.drop(5)
        }

        return value
    }
}