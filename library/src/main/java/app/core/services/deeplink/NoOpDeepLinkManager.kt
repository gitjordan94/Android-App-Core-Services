package app.core.services.deeplink

object NoOpDeepLinkManager : DeepLinkManager {
    override fun setDeepLinkListener(listener: DeepLinkListener) {
        // No-op
    }
}