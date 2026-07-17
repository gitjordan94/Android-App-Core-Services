package app.core.services.core

import app.core.services.core.model.Attribution
import app.core.services.core.model.isOrganic

/**
 * Merges attribution results from multiple providers into a final [Attribution].
 *
 * A `null` result means the provider timed out or failed and must not participate
 * in prioritization — otherwise a timed-out provider would shadow a valid
 * non-organic result from another one.
 *
 * When several providers return non-organic data, the later one in [results] wins,
 * so callers control priority via ordering (e.g. AppsFlyer after Install Referrer).
 */
internal fun mergeAttributions(results: List<Attribution?>): Attribution {
    val available = results.filterNotNull()

    return available.lastOrNull { !it.isOrganic }
        ?: available.lastOrNull()
        ?: Attribution()
}
