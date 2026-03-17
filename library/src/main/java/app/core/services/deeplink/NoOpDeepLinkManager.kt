package app.core.services.deeplink

object NoOpDeepLinkManager : DeepLinkManager {
    override fun addDeepLinkListener(listener: DeepLinkListener) {
        // No-op
    }

    override fun removeDeepLinkListener(listener: DeepLinkListener) {
        // No-op
    }
}