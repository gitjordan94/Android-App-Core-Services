package app.core.services.appsflyer.error

class AppsFlyerConversionFailureException(
    message: String?,
    cause: Throwable? = null
) : Exception(message, cause)