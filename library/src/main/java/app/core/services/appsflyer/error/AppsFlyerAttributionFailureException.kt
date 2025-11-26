package app.core.services.appsflyer.error

class AppsFlyerAttributionFailureException(
    message: String?,
    cause: Throwable? = null
) : Exception(message, cause)