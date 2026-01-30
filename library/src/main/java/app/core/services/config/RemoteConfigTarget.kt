package app.core.services.config

import app.core.services.core.model.MediaSourceType

fun interface RemoteConfigTarget {
    fun matches(data: RemoteConfigMatchingContext): Boolean

    companion object {
        fun any() = RemoteConfigTarget { true }

        fun sources(vararg sources: MediaSourceType) = RemoteConfigTarget { data ->
            sources.any { it == data.attribution.mediaSource.type }
        }

        fun countries(vararg countries: String) = RemoteConfigTarget { data ->
            data.storeCountry in countries
        }

        fun all(vararg targets: RemoteConfigTarget) = RemoteConfigTarget { data ->
            targets.all { it.matches(data) }
        }
    }
}