package app.core.services.analytics.firebase.util

import app.core.services.consent.Consent
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType

internal fun Consent.toFirebaseConsent(): Map<ConsentType, ConsentStatus> {
    fun Boolean.toFirebaseConsent(): ConsentStatus =
        if (this) ConsentStatus.GRANTED else ConsentStatus.DENIED

    return mapOf(
        ConsentType.ANALYTICS_STORAGE to analyticsStorage.toFirebaseConsent(),
        ConsentType.AD_PERSONALIZATION to adPersonalization.toFirebaseConsent(),
        ConsentType.AD_USER_DATA to adUserData.toFirebaseConsent(),
        ConsentType.AD_STORAGE to adStorage.toFirebaseConsent(),
    )
}