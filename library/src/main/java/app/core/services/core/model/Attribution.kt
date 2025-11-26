package app.core.services.core.model

data class Attribution(
    val mediaSource: MediaSource,
    val ad: String? = null,
    val adGroup: String? = null,
    val campaign: String? = null,
    val deepLinkValue: String? = null,
    val rawData: Map<String, Any?>? = null,
    val attributionSource: AttributionSource? = null,
)

val Attribution?.isOrganic
    get() = this?.mediaSource?.type == MediaSourceType.ORGANIC