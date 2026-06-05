package app.core.services.appsflyer

internal sealed class ConversionDataResult {
    data class Success(val data: Map<String, Any?>?) : ConversionDataResult()
    data class Fail(val errorMessage: String?) : ConversionDataResult()
}