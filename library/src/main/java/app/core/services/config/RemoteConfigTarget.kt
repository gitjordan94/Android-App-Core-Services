package app.core.services.config

import app.core.services.core.model.Attribution
import app.core.services.core.model.MediaSourceType

fun interface RemoteConfigTarget {
    fun matches(attribution: Attribution): Boolean

    companion object {
        fun any() = RemoteConfigTarget { true }

        fun sources(vararg sources: MediaSourceType) = RemoteConfigTarget { attribution ->
            sources.any { it == attribution.mediaSource.type }
        }
    }
}