package app.core.services.core

import android.os.SystemClock
import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource
import app.core.services.core.model.MediaSource
import app.core.services.core.model.MediaSourceType
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RaceAttributionsTest {

    private val nonOrganicReferrer = Attribution(
        mediaSource = MediaSource(value = "facebook_ads", type = MediaSourceType.FACEBOOK),
        campaign = "referrer_campaign",
        attributionSource = AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
    )

    private val nonOrganicAppsFlyer = Attribution(
        mediaSource = MediaSource(value = "facebook_ads", type = MediaSourceType.FACEBOOK),
        campaign = "af_campaign",
        attributionSource = AttributionSource.APPSFLYER,
    )

    private val farFutureDeadline = SystemClock.elapsedRealtime() + 60_000L
    private val alreadyExpiredDeadline = SystemClock.elapsedRealtime() - 1L

    @Test
    fun `both providers succeed, later one wins`() = runBlocking {
        val internal = CompletableDeferred(nonOrganicReferrer as Attribution?)
        val external = CompletableDeferred(nonOrganicAppsFlyer as Attribution?)

        val result = raceAttributions(internal, external, farFutureDeadline)

        assertEquals(nonOrganicAppsFlyer, result)
    }

    @Test
    fun `internal succeeds but external throws a genuine CancellationException — internal result must survive`() =
        runBlocking {
            val internal = CompletableDeferred(nonOrganicReferrer as Attribution?)
            val external = CompletableDeferred<Attribution?>()
            external.completeExceptionally(CancellationException("simulated real cancellation"))

            val result = raceAttributions(internal, external, farFutureDeadline)

            assertEquals(nonOrganicReferrer, result)
        }

    @Test
    fun `internal succeeds but external genuinely times out — internal result still wins`() = runBlocking {
        val internal = CompletableDeferred(nonOrganicReferrer as Attribution?)
        val external = CompletableDeferred<Attribution?>() // never completes

        val result = raceAttributions(internal, external, alreadyExpiredDeadline)

        assertEquals(nonOrganicReferrer, result)
    }

    @Test
    fun `both providers fail — falls back to empty organic attribution`() = runBlocking {
        val internal = CompletableDeferred<Attribution?>()
        internal.completeExceptionally(CancellationException("simulated real cancellation"))
        val external = CompletableDeferred<Attribution?>()
        external.completeExceptionally(RuntimeException("boom"))

        val result = raceAttributions(internal, external, farFutureDeadline)

        assertEquals(Attribution(), result)
        assertEquals(MediaSourceType.ORGANIC, result.mediaSource.type)
        assertEquals(null, result.attributionSource)
    }
}