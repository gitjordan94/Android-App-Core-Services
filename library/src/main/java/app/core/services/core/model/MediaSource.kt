package app.core.services.core.model

data class MediaSource(
    val value: String,
    val type: MediaSourceType,
) {
    constructor() : this(value = "organic", type = MediaSourceType.ORGANIC)
}