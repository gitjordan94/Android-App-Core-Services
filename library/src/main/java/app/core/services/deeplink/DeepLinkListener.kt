package app.core.services.deeplink

/**
 * A functional interface for listening to the results of a deep link resolution.
 * Implement this interface to receive a [DeepLinkResult] when a deep link is processed.
 */
fun interface DeepLinkListener {
    fun onDeepLink(deepLink: DeepLinkResult)
}