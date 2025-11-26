package app.core.services.core

import app.core.services.attribution.AttributionParser
import app.core.services.attribution.DefaultMediaSourceParser
import app.core.services.attribution.MediaSourceParser
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER
import app.core.services.core.model.MediaSource
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
            URLDecoder.decode(data.trim(), "UTF-8")
        } catch (e: Throwable) {
            Timber.e(e)
            data
        }

        val params = referrer
            .split("&")
            .mapNotNull {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

        return Attribution(
            mediaSource = mediaSourceParser.parse(determineMediaSource(params)),
            campaign = params["c"],
            ad = params["af_ad"],
            adGroup = params["af_adset"],
            deepLinkValue = params["deep_link_value"],
            rawData = params,
            attributionSource = GOOGLE_PLAY_INSTALL_REFERRER
        )
    }

    private fun determineMediaSource(params: Map<String, String>): String? {
        if (isGoogleAdsReferrer(params)) {
            return "googleadwords_int"
        }

        if (params["utm_medium"]?.equals("organic", ignoreCase = true) == true) {
            return null
        }

        return params["utm_source"] ?: params["pid"]
    }

    private fun isGoogleAdsReferrer(params: Map<String, String>): Boolean {
        if (params.size != 3) {
            return false
        }

        return !params["gclid"].isNullOrBlank() &&
                !params["gbraid"].isNullOrBlank() &&
                !params["gad_source"].isNullOrBlank()
    }
}