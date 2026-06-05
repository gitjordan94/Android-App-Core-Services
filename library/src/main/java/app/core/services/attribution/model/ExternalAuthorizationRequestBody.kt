package app.core.services.attribution.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalAuthorizationRequestBody(
    @SerialName("userId") val userId: String,
    @SerialName("productUserId") val productUserId: String,
)