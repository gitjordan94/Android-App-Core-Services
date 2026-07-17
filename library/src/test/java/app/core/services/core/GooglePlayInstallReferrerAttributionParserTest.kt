package app.core.services.core

import app.core.services.attribution.GooglePlayInstallReferrerAttributionParser
import app.core.services.core.model.AttributionSource
import app.core.services.core.model.MediaSource
import app.core.services.core.model.MediaSourceType
import app.core.services.core.model.MediaSources
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class GooglePlayInstallReferrerAttributionParserTest {
    private val parser = GooglePlayInstallReferrerAttributionParser()

    @Test
    fun `parse returns empty attribution when data is null`() {
        val result = parser.parse(null)

        assertEquals(MediaSource(), result.mediaSource)
        assertEquals(
            AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
            result.attributionSource
        )
        assertNull(result.campaign)
        assertNull(result.ad)
        assertNull(result.adGroup)
        assertNull(result.deepLinkValue)
    }

    @Test
    fun `parse returns empty attribution when data is blank`() {
        val result = parser.parse("   ")

        assertEquals(MediaSource(), result.mediaSource)
        assertEquals(
            AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
            result.attributionSource
        )
        assertNull(result.campaign)
        assertNull(result.ad)
        assertNull(result.adGroup)
        assertNull(result.deepLinkValue)
    }

    @Test
    fun `parse detects tiktok media source for long raw tiktok referrer`() {
        val rawReferrer = "tiktokglobal_E_C_P_CsEBszHJuuwDxmU9HswHlCNjnTBSrjPvCJ1j6ZiN1x"

        val result = parser.parse(rawReferrer)

        assertEquals(
            MediaSource(MediaSources.TIKTOK_GLOBAL, MediaSourceType.TIKTOK),
            result.mediaSource
        )

        assertEquals(
            AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
            result.attributionSource
        )
    }

    @Test
    fun `parse returns organic for random referrer string`() {
        val randomReferrer = "some_random_garbage_string_12345"

        val result = parser.parse(randomReferrer)

        assertEquals(MediaSource(), result.mediaSource)

        assertNull(result.campaign)
        assertNull(result.ad)
        assertNull(result.adGroup)
        assertNull(result.deepLinkValue)

        assertEquals(
            AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
            result.attributionSource
        )
    }

    @Test
    fun `parse returns organic when utm params are not set placeholders`() {
        val referrer = "utm_source=(not set)&utm_medium=(not set)"

        val result = parser.parse(referrer)

        assertEquals(MediaSource(), result.mediaSource)
        assertEquals(
            AttributionSource.GOOGLE_PLAY_INSTALL_REFERRER,
            result.attributionSource
        )
    }

    @Test
    fun `parse returns organic when utm source is not set without parentheses`() {
        val referrer = "utm_source=not set&utm_medium=cpc"

        val result = parser.parse(referrer)

        assertEquals(MediaSource(), result.mediaSource)
    }

    @Test
    fun `parse returns organic for canonical google play organic referrer`() {
        val referrer = "utm_source=google-play&utm_medium=organic"

        val result = parser.parse(referrer)

        assertEquals(MediaSource(), result.mediaSource)
    }

    @Test
    fun `parse falls back to pid when utm source is a placeholder`() {
        val referrer = "utm_source=(not set)&pid=snapchat_int"

        val result = parser.parse(referrer)

        assertEquals(MediaSourceType.SNAPCHAT, result.mediaSource.type)
        assertEquals("snapchat_int", result.mediaSource.value)
    }

    @Test
    fun `parse keeps non-organic media source for real utm source`() {
        val referrer = "utm_source=snapchat&utm_medium=cpc&c=summer_promo"

        val result = parser.parse(referrer)

        assertEquals(MediaSourceType.SNAPCHAT, result.mediaSource.type)
        assertEquals("summer_promo", result.campaign)
    }

    @Test
    fun `parse detects tiktokglobal when referrer looks like key value`() {
        val referrer = "tiktokglobal=something"

        val result = parser.parse(referrer)

        assertEquals(
            MediaSource(MediaSources.TIKTOK_GLOBAL, MediaSourceType.TIKTOK),
            result.mediaSource
        )

        assertEquals(
            mapOf("tiktokglobal" to "something"),
            result.rawData
        )

        assertNull(result.campaign)
        assertNull(result.ad)
        assertNull(result.adGroup)
        assertNull(result.deepLinkValue)
    }
}