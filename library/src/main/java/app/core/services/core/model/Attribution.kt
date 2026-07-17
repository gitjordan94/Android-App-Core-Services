package app.core.services.core.model

data class Attribution(
    val mediaSource: MediaSource,
    val ad: String? = null,
    val adGroup: String? = null,
    val campaign: String? = null,
    val deepLinkValue: String? = null,
    val rawData: Map<String, Any?>? = null,
    val attributionSource: AttributionSource? = null,
) {
    constructor() : this(
        mediaSource = MediaSource(
            value = MediaSources.ORGANIC,
            type = MediaSourceType.ORGANIC
        )
    )
}

val Attribution.isOrganic
    get() = mediaSource.type == MediaSourceType.ORGANIC