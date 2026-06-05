package app.core.services.deeplink

sealed class DeepLinkResult {
    data class Found(val deepLink: DeepLink) : DeepLinkResult()

    data object NotFound : DeepLinkResult()

    data class Error(val error: Throwable) : DeepLinkResult()
}