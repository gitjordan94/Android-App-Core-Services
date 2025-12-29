package app.core.services.appsflyer

internal sealed class ConversionDataResult {
    data object Loading : ConversionDataResult()

    data class Success(val data: Map<String, Any?>?) : ConversionDataResult()

    data class Error(val errorMessage: String?) : ConversionDataResult()
}