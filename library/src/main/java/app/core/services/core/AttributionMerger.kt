package app.core.services.core

import app.core.services.common.awaitUntil
import app.core.services.core.model.Attribution
import app.core.services.core.model.isOrganic
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

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

/**
 * Races [internalAttributionDeferred] and [externalAttributionDeferred] against
 * [providersDeadline] and merges whatever completed in time.
 *
 * Each provider is isolated via [supervisorScope] + [runCatching]: a failure or
 * cancellation reaching one provider must never discard a sibling's already-successful
 * result. Without this isolation, [internalAttributionDeferred] and [externalAttributionDeferred]
 * would be plain sibling coroutines under one job — a genuine `CancellationException` propagating out of
 * one provider (e.g. from [awaitUntil] rethrowing it) would cancel the whole race,
 * including a sibling that had already completed.
 */
internal suspend fun raceAttributions(
    internalAttributionDeferred: Deferred<Attribution?>,
    externalAttributionDeferred: Deferred<Attribution?>,
    providersDeadline: Long,
): Attribution = supervisorScope {
    val internal = async {
        runCatching { internalAttributionDeferred.awaitUntil(providersDeadline) }
            .onFailure { Timber.e(it, "[internal_attribution] failed, ignoring") }
            .getOrNull()
    }

    val external = async {
        runCatching { externalAttributionDeferred.awaitUntil(providersDeadline) }
            .onFailure { Timber.e(it, "[external_attribution] failed, ignoring") }
            .getOrNull()
    }

    mergeAttributions(listOf(internal, external).awaitAll())
}