package app.core.services.attribution.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttributionInstallRequestBody(
    @SerialName("sdkVersion") val sdkVersion: String,
    @SerialName("osVersion") val osVersion: String,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("limitAdTracking") val limitAdTracking: Boolean,
    @SerialName("advertisingId") val advertisingId: String,
    @SerialName("appsflyerId") val appsflyerId: String,
    @SerialName("storeCountry") val storeCountry: String,
    @SerialName("environment") val environment: String,
    @SerialName("externalAuthorization") val externalAuthorization: Boolean,
)