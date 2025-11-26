package app.core.services.deeplink

interface DeepLinkManager {
    fun addDeepLinkListener(listener: DeepLinkListener)

    fun removeDeepLinkListener(listener: DeepLinkListener)
}