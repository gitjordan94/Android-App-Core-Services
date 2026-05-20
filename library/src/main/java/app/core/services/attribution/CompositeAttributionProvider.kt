package app.core.services.attribution

import app.core.services.core.model.Attribution
import app.core.services.core.model.isOrganic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * A composite implementation of [AttributionProvider] that queries multiple providers in parallel
 * and aggregates their results based on specific priority logic.
 *
 * This class is designed to fetch attribution data from various sources (e.g., AppsFlyer,
 * Play Store Referrer, Cache) concurrently to minimize total wait time.
 *
 * **Resolution Strategy:**
 * 1. All providers are queried in parallel with a strict [timeout].
 * 2. Results are filtered to remove failures (nulls).
 * 3. The first **non-organic** result is returned if available (highest priority).
 * 4. If only **organic** results are found, the first one is returned (fallback).
 * 5. If no providers return data, `null` is returned.
 *
 * @property timeout The maximum time in milliseconds to wait for each provider.
 * @property providers The list of individual attribution providers to query.
 */
internal class CompositeAttributionProvider(
    private val timeout: Long = 10_000,
    private val providers: List<AttributionProvider>,
) : AttributionProvider {

    /**
     * Orchestrates the fetching of attribution data from all registered providers.
     *
     * @return The resolved [Attribution] object based on the priority strategy,
     * or `null` if all providers fail or return no data.
     */
    override suspend fun provide(): Attribution? {
        return coroutineScope {
            val results = providers
                .map { provider -> async { fetchFromProvider(provider) } }
                .awaitAll()
                .filterNotNull()

            // Priority: First non-organic source (e.g., a specific ad campaign)
            // Fallback: Any available source (likely organic)
            val attribution = results.firstOrNull { !it.isOrganic } ?: results.firstOrNull()

            Timber.d("Final resolved attribution: $attribution")

            attribution
        }
    }

    private suspend fun fetchFromProvider(provider: AttributionProvider): Attribution? {
        val providerName = provider::class.simpleName
        return try {
            withTimeout(timeout) {
                provider.provide()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "Attribution provider '$providerName' timed out after ${timeout}ms")
            null
        } catch (e: Throwable) {
            Timber.e(e, "Attribution provider '$providerName' failed unexpectedly")
            null
        }
    }
}