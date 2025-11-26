package app.core.services.config.model

import app.core.services.config.RemoteConfigTarget
import app.core.services.core.model.Attribution

internal class RemoteConfigParameter(
    val key: String,
    val defaultValue: String,
    val target: RemoteConfigTarget? = null,
) {
    fun getValue(remoteValue: String, attribution: Attribution?): String {
        val value = if (target == null || attribution == null || target.matches(attribution)) {
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