package app.core.services.appsflyer.attribution

import app.core.services.attribution.AttributionParser
import app.core.services.attribution.DefaultMediaSourceParser
import app.core.services.attribution.MediaSourceParser
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource.APPSFLYER
import app.core.services.core.model.MediaSource

internal class AppsFlyerAttributionParser(
    private val mediaSourceParser: MediaSourceParser = DefaultMediaSourceParser,
) : AttributionParser<Map<String, Any?>> {
    override fun parse(data: Map<String, Any?>): Attribution {
        if (data["af_status"] == "Organic") {
            return Attribution(
                mediaSource = MediaSource(),
                attributionSource = APPSFLYER
            )
        }

        val deepLinkValue = (data["deep_link_value"]
            ?: data["af_dp"]) as? String

        val mediaSource = data["media_source"] as? String
        val campaign = data["campaign"] as? String
        var ad = data["af_ad"] as? String
        if (ad.isNullOrBlank()) {
            ad = data["adgroup"] as? String
        }

        val adGroup = data["af_adset"] as? String

        return Attribution(
            mediaSource = mediaSourceParser.parse(mediaSource),
            campaign = campaign,
            adGroup = adGroup,
            ad = ad,
            deepLinkValue = deepLinkValue,
            rawData = data,
            attributionSource = APPSFLYER
        )
    }
}