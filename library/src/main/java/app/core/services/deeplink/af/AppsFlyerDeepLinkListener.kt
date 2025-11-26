package app.core.services.deeplink.af

import androidx.core.net.toUri
import app.core.services.attribution.DefaultMediaSourceParser
import app.core.services.attribution.MediaSourceParser
import app.core.services.common.mapOfNotNull
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource
import app.core.services.deeplink.DeepLink
import app.core.services.deeplink.DeepLinkListener
import app.core.services.deeplink.DeepLinkResult
import kotlinx.io.IOException
import timber.log.Timber
import java.util.Collections
import com.appsflyer.deeplink.DeepLink as AfDeepLink
import com.appsflyer.deeplink.DeepLinkListener as AfDeepLinkListener
import com.appsflyer.deeplink.DeepLinkResult as AfDeepLinkResult

internal class AppsFlyerDeepLinkListener(
    private val mediaSourceParser: MediaSourceParser = DefaultMediaSourceParser
) : AfDeepLinkListener {
    private val delegates = Collections.synchronizedList(mutableListOf<DeepLinkListener>())

    fun addDelegate(listener: DeepLinkListener) {
        Timber.d("Adding new deep link delegate: $listener")
        delegates.add(listener)
    }

    fun removeDelegate(listener: DeepLinkListener) {
        Timber.d("Removing deep link delegate: $listener")
        delegates.remove(listener)
    }

    override fun onDeepLinking(deepLinkResult: AfDeepLinkResult) {
        val deepLinkResult = when (deepLinkResult.status) {
            AfDeepLinkResult.Status.FOUND -> {
                Timber.d("Deep link found: ${deepLinkResult.deepLink}")
                val deepLink = parseDeepLink(deepLinkResult.deepLink)
                deepLink
            }

            AfDeepLinkResult.Status.NOT_FOUND -> {
                Timber.d("Deep link not found")
                DeepLinkResult.NotFound
            }

            AfDeepLinkResult.Status.ERROR -> {
                val exception = when (deepLinkResult.error) {
                    AfDeepLinkResult.Error.TIMEOUT -> IOException("Deep link timeout")
                    AfDeepLinkResult.Error.NETWORK -> IOException("Deep link network error")
                    AfDeepLinkResult.Error.HTTP_STATUS_CODE -> IOException("Deep link http status code error")
                    AfDeepLinkResult.Error.UNEXPECTED -> IllegalStateException("Deep link unexpected error")
                    AfDeepLinkResult.Error.DEVELOPER_ERROR -> IllegalStateException("Deep link developer error")
                    null -> IllegalStateException("Deep link error is null")
                }

                Timber.e(exception)

                DeepLinkResult.Error(exception)
            }
        }

        delegates.toList().forEach { delegate ->
            delegate.onDeepLinkResult(deepLinkResult)
        }
    }

    private fun parseDeepLink(deepLink: AfDeepLink?): DeepLinkResult {
        try {
            val link = deepLink?.getStringValue("link") ?: return DeepLinkResult.NotFound

            val attribution = Attribution(
                mediaSource = mediaSourceParser.parse(deepLink.mediaSource),
                campaign = deepLink.campaign,
                deepLinkValue = deepLink.deepLinkValue,
                attributionSource = AttributionSource.APPSFLYER,
            )

            val params = mapOfNotNull(
                "media_source" to deepLink.mediaSource,
                "campaign" to deepLink.campaign,
                "campaign_id" to deepLink.campaignId,
                "deep_link_value" to deepLink.deepLinkValue,
                "af_sub1" to deepLink.afSub1,
                "af_sub2" to deepLink.afSub2,
                "af_sub3" to deepLink.afSub3,
                "af_sub4" to deepLink.afSub4,
                "af_sub5" to deepLink.afSub5,
                "match_type" to deepLink.matchType,
                "click_http_referrer" to deepLink.clickHttpReferrer
            )

            return DeepLinkResult.Found(
                DeepLink(
                    uri = link.toUri(),
                    params = params,
                    attribution = attribution,
                )
            )
        } catch (e: Throwable) {
            Timber.e(e)
            return DeepLinkResult.Error(e)
        }
    }
}