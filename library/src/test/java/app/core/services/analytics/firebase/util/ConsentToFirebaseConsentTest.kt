package app.core.services.analytics.firebase.util

import app.core.services.consent.Consent
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import junit.framework.TestCase.assertEquals
import org.junit.Test

class ConsentToFirebaseConsentTest {

    @Test
    fun `all granted maps every consent type to GRANTED`() {
        val consent = Consent(
            analyticsStorage = true,
            adPersonalization = true,
            adStorage = true,
            adUserData = true
        )

        // when
        val result = consent.toFirebaseConsent()

        // then
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_USER_DATA])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_PERSONALIZATION])
        assertEquals(4, result.size)
    }

    @Test
    fun `all denied maps every consent type to DENIED`() {
        val consent = Consent(
            analyticsStorage = false,
            adPersonalization = false,
            adStorage = false,
            adUserData = false
        )

        // when
        val result = consent.toFirebaseConsent()

        // then
        assertEquals(ConsentStatus.DENIED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_USER_DATA])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_PERSONALIZATION])
        assertEquals(4, result.size)
    }

    @Test
    fun `analyticsStorage maps to ANALYTICS_STORAGE independently`() {
        val consent = Consent(
            analyticsStorage = true,
            adPersonalization = false,
            adStorage = false,
            adUserData = false
        )

        val result = consent.toFirebaseConsent()

        assertEquals(ConsentStatus.GRANTED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_USER_DATA])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_PERSONALIZATION])
    }

    @Test
    fun `adStorage maps to AD_STORAGE independently`() {
        val consent = Consent(
            analyticsStorage = false,
            adPersonalization = false,
            adStorage = true,
            adUserData = false
        )

        val result = consent.toFirebaseConsent()

        assertEquals(ConsentStatus.DENIED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_USER_DATA])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_PERSONALIZATION])
    }

    @Test
    fun `adUserData maps to AD_USER_DATA independently`() {
        val consent = Consent(
            analyticsStorage = false,
            adPersonalization = false,
            adStorage = false,
            adUserData = true
        )

        val result = consent.toFirebaseConsent()

        assertEquals(ConsentStatus.DENIED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_USER_DATA])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_PERSONALIZATION])
    }

    @Test
    fun `adPersonalization maps to AD_PERSONALIZATION independently`() {
        val consent = Consent(
            analyticsStorage = false,
            adPersonalization = true,
            adStorage = false,
            adUserData = false
        )

        val result = consent.toFirebaseConsent()

        assertEquals(ConsentStatus.DENIED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_USER_DATA])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_PERSONALIZATION])
    }

    @Test
    fun `mixed consent maps each field to correct status without inversion`() {
        val consent = Consent(
            analyticsStorage = true,
            adPersonalization = false,
            adStorage = true,
            adUserData = false
        )

        val result = consent.toFirebaseConsent()

        assertEquals(ConsentStatus.GRANTED, result[ConsentType.ANALYTICS_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_PERSONALIZATION])
        assertEquals(ConsentStatus.GRANTED, result[ConsentType.AD_STORAGE])
        assertEquals(ConsentStatus.DENIED, result[ConsentType.AD_USER_DATA])
    }

    @Test
    fun `result contains exactly four firebase consent types`() {
        val consent = Consent(
            analyticsStorage = true,
            adPersonalization = false,
            adStorage = true,
            adUserData = false
        )

        val result = consent.toFirebaseConsent()

        assertEquals(4, result.size)
        assertEquals(
            setOf(
                ConsentType.ANALYTICS_STORAGE,
                ConsentType.AD_STORAGE,
                ConsentType.AD_USER_DATA,
                ConsentType.AD_PERSONALIZATION
            ),
            result.keys
        )
    }
}