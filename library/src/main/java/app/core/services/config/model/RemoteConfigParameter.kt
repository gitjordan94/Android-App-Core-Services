package app.core.services.config.model

internal data class RemoteConfigParameter(
    val key: String,
    val defaultValue: String?,
    val defaultPayload: String?,
    val isStickyBucketed: Boolean = false
)