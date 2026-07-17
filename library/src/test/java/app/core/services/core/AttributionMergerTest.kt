package app.core.services.core

import app.core.services.core.model.Attribution
import app.core.services.core.model.AttributionSource
import app.core.services.core.model.MediaSource
import app.core.services.core.model.MediaSourceType
import junit.framework.TestCase.assertEquals
import org.junit.Test

class AttributionMergerTest {

    private val referrerNonOrganic = Attribution(
        mediaSource = MediaSource(value = "google_ads", type = MediaSourceType.GOOGLE),
        campaign = "referrer_campaign",
        attributionSource = AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
    )

    private val appsFlyerNonOrganic = Attribution(
        mediaSource = MediaSource(value = "facebook_ads", type = MediaSourceType.FACEBOOK),
        campaign = "af_campaign",
        attributionSource = AttributionSource.APPSFLYER,
    )

    private val referrerOrganic = Attribution(
        mediaSource = MediaSource(),
        attributionSource = AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
    )

    private val appsFlyerOrganic = Attribution(
        mediaSource = MediaSource(),
        attributionSource = AttributionSource.APPSFLYER,
    )

    @Test
    fun `non-organic referrer wins when AppsFlyer times out`() {
        val result = mergeAttributions(listOf(referrerNonOrganic, null))

        assertEquals(referrerNonOrganic, result)
    }

    @Test
    fun `non-organic referrer wins over organic AppsFlyer`() {
        val result = mergeAttributions(listOf(referrerNonOrganic, appsFlyerOrganic))

        assertEquals(referrerNonOrganic, result)
    }

    @Test
    fun `non-organic AppsFlyer wins over organic referrer`() {
        val result = mergeAttributions(listOf(referrerOrganic, appsFlyerNonOrganic))

        assertEquals(appsFlyerNonOrganic, result)
    }

    @Test
    fun `last non-organic wins when both are non-organic`() {
        val result = mergeAttributions(listOf(referrerNonOrganic, appsFlyerNonOrganic))

        assertEquals(appsFlyerNonOrganic, result)
    }

    @Test
    fun `non-organic AppsFlyer wins when referrer times out`() {
        val result = mergeAttributions(listOf(null, appsFlyerNonOrganic))

        assertEquals(appsFlyerNonOrganic, result)
    }

    @Test
    fun `organic AppsFlyer with attribution source is kept when referrer times out`() {
        val result = mergeAttributions(listOf(null, appsFlyerOrganic))

        assertEquals(appsFlyerOrganic, result)
    }

    @Test
    fun `organic referrer with attribution source is kept when AppsFlyer times out`() {
        val result = mergeAttributions(listOf(referrerOrganic, null))

        assertEquals(referrerOrganic, result)
    }

    @Test
    fun `last organic wins when both are organic`() {
        val result = mergeAttributions(listOf(referrerOrganic, appsFlyerOrganic))

        assertEquals(appsFlyerOrganic, result)
    }

    @Test
    fun `falls back to empty organic attribution when all providers time out`() {
        val result = mergeAttributions(listOf(null, null))

        assertEquals(Attribution(), result)
        assertEquals(MediaSourceType.ORGANIC, result.mediaSource.type)
        assertEquals(null, result.attributionSource)
    }

    @Test
    fun `falls back to empty organic attribution when results are empty`() {
        val result = mergeAttributions(emptyList())

        assertEquals(Attribution(), result)
    }
}
