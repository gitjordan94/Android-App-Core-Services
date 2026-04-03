package app.core.services.agesignals.model

class AgeSignalsException(
    message: String?,
    val errorCode: AgeSignalsErrorCode,
) : Exception(message)