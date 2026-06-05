package app.core.services.attribution

import app.core.services.core.model.MediaSource
import app.core.services.core.model.MediaSourceType

internal interface MediaSourceParser {
    fun parse(raw: String?): MediaSource
}

internal object DefaultMediaSourceParser : MediaSourceParser {
    override fun parse(raw: String?): MediaSource {
        if (raw.isNullOrBlank()) return MediaSource()

        val normalizedRaw = raw
            .replace(" ", "_")
            .lowercase()

        val sources = mapOf(
            "organic" to MediaSourceType.ORGANIC,
            "google" to MediaSourceType.GOOGLE,
            "facebook" to MediaSourceType.FACEBOOK,
            "fb" to MediaSourceType.FACEBOOK,
            "instagram" to MediaSourceType.FACEBOOK,
            "ig" to MediaSourceType.FACEBOOK,
            "tiktok" to MediaSourceType.TIKTOK,
            "bytedanceglobal_int" to MediaSourceType.TIKTOK,
        )

        val mediaSourceType = sources.entries
            .firstOrNull { normalizedRaw.contains(it.key) }
            ?.value
            ?: MediaSourceType.UNKNOWN

        return MediaSource(value = raw, type = mediaSourceType)
    }
}