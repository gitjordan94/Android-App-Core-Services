package app.core.services.core.util

import app.core.services.common.mapOfNotNull
import app.core.services.core.model.Attribution

fun Attribution.toMap(): Map<String, String> {
    return mapOfNotNull(
        "network" to mediaSource.value,
        "campaignName" to campaign,
        "adGroupName" to adGroup,
        "ad" to ad,
        "deep_link_value" to deepLinkValue,
        "attribution_source" to attributionSource?.value
    )
}