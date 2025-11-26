package app.core.services.deeplink.af

import app.core.services.deeplink.DeepLinkListener
import app.core.services.deeplink.DeepLinkManager
import com.appsflyer.AppsFlyerLib

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
    appsFlyer: AppsFlyerLib = AppsFlyerLib.getInstance()
) : DeepLinkManager {
    private val appsFlyerDeepLinkListener = AppsFlyerDeepLinkListener()

    init {
        appsFlyer.subscribeForDeepLink(appsFlyerDeepLinkListener)
    }

    override fun addDeepLinkListener(listener: DeepLinkListener) {
        appsFlyerDeepLinkListener.addDelegate(listener)
    }

    override fun removeDeepLinkListener(listener: DeepLinkListener) {
        appsFlyerDeepLinkListener.removeDelegate(listener)
    }
}