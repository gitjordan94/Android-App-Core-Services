package app.core.services.agesignals.util

import app.core.services.agesignals.model.AgeSignalsErrorCode
import app.core.services.agesignals.model.AgeSignalsException
import com.google.android.play.agesignals.model.AgeSignalsErrorCode as ExternalAgeSignalsErrorCode
import com.google.android.play.agesignals.AgeSignalsException as ExternalAgeSignalsException

internal fun ExternalAgeSignalsException.toInternal(): AgeSignalsException {
    return AgeSignalsException(
        message = message,
        errorCode = when (errorCode) {
            ExternalAgeSignalsErrorCode.API_NOT_AVAILABLE -> AgeSignalsErrorCode.API_NOT_AVAILABLE
            ExternalAgeSignalsErrorCode.PLAY_STORE_NOT_FOUND -> AgeSignalsErrorCode.PLAY_STORE_NOT_FOUND
            ExternalAgeSignalsErrorCode.NO_ERROR -> AgeSignalsErrorCode.NO_ERROR
            ExternalAgeSignalsErrorCode.NETWORK_ERROR -> AgeSignalsErrorCode.NETWORK_ERROR
            ExternalAgeSignalsErrorCode.PLAY_SERVICES_NOT_FOUND -> AgeSignalsErrorCode.PLAY_SERVICES_NOT_FOUND
            ExternalAgeSignalsErrorCode.CANNOT_BIND_TO_SERVICE -> AgeSignalsErrorCode.CANNOT_BIND_TO_SERVICE
            ExternalAgeSignalsErrorCode.PLAY_STORE_VERSION_OUTDATED -> AgeSignalsErrorCode.PLAY_STORE_VERSION_OUTDATED
            ExternalAgeSignalsErrorCode.PLAY_SERVICES_VERSION_OUTDATED -> AgeSignalsErrorCode.PLAY_SERVICES_VERSION_OUTDATED
            ExternalAgeSignalsErrorCode.CLIENT_TRANSIENT_ERROR -> AgeSignalsErrorCode.CLIENT_TRANSIENT_ERROR
            ExternalAgeSignalsErrorCode.APP_NOT_OWNED -> AgeSignalsErrorCode.APP_NOT_OWNED
            ExternalAgeSignalsErrorCode.SDK_VERSION_OUTDATED -> AgeSignalsErrorCode.SDK_VERSION_OUTDATED
            ExternalAgeSignalsErrorCode.INTERNAL_ERROR -> AgeSignalsErrorCode.INTERNAL_ERROR
            else -> AgeSignalsErrorCode.UNKNOWN
        },
    )
}