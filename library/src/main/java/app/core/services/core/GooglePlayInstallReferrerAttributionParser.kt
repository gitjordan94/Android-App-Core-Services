package app.core.services.core

import app.core.services.attribution.AttributionParser
import app.core.services.attribution.DefaultMediaSourceParser
import app.core.services.attribution.MediaSourceParser
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER
import app.core.services.core.model.MediaSource
import app.core.services.core.model.MediaSources
import timber.log.Timber
import java.net.URLDecoder

internal class GooglePlayInstallReferrerAttributionParser(
    private val mediaSourceParser: MediaSourceParser = DefaultMediaSourceParser,
) : AttributionParser<String?> {
    override fun parse(data: String?): Attribution {
        if (data.isNullOrBlank()) {
            return Attribution(
                mediaSource = MediaSource(),
                attributionSource = GOOGLE_PLAY_INSTALL_REFERRER
            )
        }

        val referrer = try {
            URLDecoder.decode(data.trim(), Charsets.UTF_8.name())
        } catch (e: Throwable) {
            Timber.e(e)
            data.trim()
        }

        val params = parseParams(referrer)

        val rawMediaSource = if (params.isNotEmpty()) {
            getMediaSourceByParams(params) ?: getMediaSourceByValue(referrer)
        } else {
            getMediaSourceByValue(referrer) ?: getMediaSourceByParams(params)
        }

        return Attribution(
            mediaSource = mediaSourceParser.parse(rawMediaSource),
            campaign = params["c"],
            ad = params["af_ad"],
            adGroup = params["af_adset"],
            deepLinkValue = params["deep_link_value"],
            rawData = params,
            attributionSource = GOOGLE_PLAY_INSTALL_REFERRER
        )
    }

    private fun parseParams(referrer: String): Map<String, String> {
        return referrer
            .split("&")
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null

                val key = part.substring(0, index)
                val value = part.substring(index + 1)

                key to value
            }
            .toMap()
    }

    private fun getMediaSourceByValue(value: String): String? {
        val normalized = value.trim().lowercase()

        return when {
            normalized.startsWith("tiktokglobal") -> MediaSources.TIKTOK_GLOBAL
            normalized.startsWith("tiktok") -> MediaSources.TIKTOK
            else -> null
        }
    }

    private fun getMediaSourceByParams(params: Map<String, String>): String? {
        if (params.isEmpty()) {
            return null
        }

        if (isGoogleAdsReferrer(params)) {
            return MediaSources.GOOGLE
        }

        if (params["utm_medium"]?.equals(MediaSources.ORGANIC, ignoreCase = true) == true) {
            return null
        }

        return params["utm_source"] ?: params["pid"]
    }

    private fun isGoogleAdsReferrer(params: Map<String, String>): Boolean {
        if (params.size < 3) {
            return false
        }

        return !params["gclid"].isNullOrBlank() &&
                !params["gbraid"].isNullOrBlank() &&
                !params["gad_source"].isNullOrBlank()
    }
}