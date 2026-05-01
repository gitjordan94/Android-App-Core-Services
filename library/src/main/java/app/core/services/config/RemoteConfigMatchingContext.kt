package app.core.services.config

import app.core.services.core.model.Attribution

data class RemoteConfigMatchingContext(
    val attribution: Attribution,
    val storeCountry: String?,
)