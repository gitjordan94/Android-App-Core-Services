package app.core.services.deeplink.af

import androidx.core.net.toUri
import app.core.services.attribution.DefaultMediaSourceParser
import app.core.services.attribution.MediaSourceParser
import app.core.services.common.mapOfNotNull
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource
import app.core.services.deeplink.DeepLink
import app.core.services.deeplink.DeepLinkListener
import app.core.services.deeplink.DeepLinkManager
import app.core.services.deeplink.DeepLinkResult
import com.appsflyer.AppsFlyerLib
import kotlinx.io.IOException
import timber.log.Timber
import com.appsflyer.share.deeplink.DeepLink as AfDeepLink
import com.appsflyer.share.deeplink.DeepLinkResult as AfDeepLinkResult

/**
 * Manages deep link handling specifically for the AppsFlyer service.
 *
 * This class acts as an adapter between the application's generic [DeepLinkManager]
 * interface and the AppsFlyer SDK's deep linking mechanism. It subscribes to AppsFlyer's
 * deep link events upon initialization and delegates the received deep link data
 * to any registered [DeepLinkListener]s.
 *
 * @param appsFlyer An instance of [AppsFlyerLib] used to subscribe for deep link events.
 */
internal class AppsFlyerDeepLinkManager(
    private val appsFlyer: AppsFlyerLib = AppsFlyerLib.getInstance(),
    private val mediaSourceParser: MediaSourceParser = DefaultMediaSourceParser
) : DeepLinkManager {
    override fun setDeepLinkListener(listener: DeepLinkListener) {
        appsFlyer.subscribeForDeepLink { deepLinkResult ->
            val deepLink = when (deepLinkResult.status) {
                AfDeepLinkResult.Status.FOUND -> {
                    Timber.d("Deep link found: ${deepLinkResult.deepLink}")
                    parseDeepLink(deepLinkResult.deepLink)
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

            listener.onDeepLink(deepLink)
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