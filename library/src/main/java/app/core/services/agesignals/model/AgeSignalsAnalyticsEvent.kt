package app.core.services.agesignals.model

import app.core.services.BuildConfig
import app.core.services.analytics.AnalyticsEvent
import app.core.services.common.mapOfNotNull

internal sealed class AgeSignalsAnalyticsEvent(
    val status: String,
) : AnalyticsEvent {
    override val type = "age_signals_request"
    override val properties = mapOf(
        "status" to status,
        "sdk_version" to BuildConfig.AGE_SIGNALS_SDK_VERSION
    )

    data object Success : AgeSignalsAnalyticsEvent("success")

    data class Error(private val cause: AgeSignalsException) : AgeSignalsAnalyticsEvent("error") {
        override val properties: Map<String, String>
            get() = super.properties + mapOfNotNull(
                "error_code" to when (cause.errorCode) {
                    AgeSignalsErrorCode.NO_ERROR -> "NO_ERROR"
                    AgeSignalsErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
                    AgeSignalsErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
                    AgeSignalsErrorCode.API_NOT_AVAILABLE -> "API_NOT_AVAILABLE"
                    AgeSignalsErrorCode.PLAY_STORE_NOT_FOUND -> "PLAY_STORE_NOT_FOUND"
                    AgeSignalsErrorCode.PLAY_SERVICES_NOT_FOUND -> "PLAY_SERVICES_NOT_FOUND"
                    AgeSignalsErrorCode.CANNOT_BIND_TO_SERVICE -> "CANNOT_BIND_TO_SERVICE"
                    AgeSignalsErrorCode.PLAY_STORE_VERSION_OUTDATED -> "PLAY_STORE_VERSION_OUTDATED"
                    AgeSignalsErrorCode.PLAY_SERVICES_VERSION_OUTDATED -> "PLAY_SERVICES_VERSION_OUTDATED"
                    AgeSignalsErrorCode.CLIENT_TRANSIENT_ERROR -> "CLIENT_TRANSIENT_ERROR"
                    AgeSignalsErrorCode.APP_NOT_OWNED -> "APP_NOT_OWNED"
                    AgeSignalsErrorCode.SDK_VERSION_OUTDATED -> "SDK_VERSION_OUTDATED"
                    AgeSignalsErrorCode.UNKNOWN -> "UNKNOWN"
                },
                "error_message" to cause.message,
            )
    }
}