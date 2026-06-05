package app.core.services.firebase

import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvent
import app.core.services.analytics.AnalyticsEvents
import app.core.services.analytics.PurchaseEventLogger
import app.core.services.analytics.firebase.util.toFirebaseConsent
import app.core.services.billing.model.Purchase
import app.core.services.common.toBundle
import app.core.services.consent.Consent
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.tasks.await

internal object FirebaseAnalytics : Analytics, PurchaseEventLogger, FirebaseAppInstanceId {
    private val analytics by lazy { Firebase.analytics }

    internal fun setUserId(userId: String?) {
        analytics.setUserId(userId)
    }

    internal fun setConsent(consent: Consent) {
        analytics.setConsent(consent.toFirebaseConsent())
    }

    override fun setUserProperties(properties: Map<String, Any?>?) {
        // do nothing
    }

    override fun logEvent(event: String, properties: Map<String, Any?>?) {
        analytics.logEvent(event, properties?.toBundle())
    }

    override fun logEvent(event: AnalyticsEvent) {
        logEvent(event.type, event.properties)
    }

    override fun logPurchase(purchase: Purchase) {
        if (purchase.price.amountMicros == 0L) {
            logEvent(AnalyticsEvents.TRIAL_STARTED)
        } else {
            logEvent(
                event = "in_app_purchased",
                properties = mapOf(
                    "productId" to purchase.product.id,
                    "purchaseId" to purchase.purchaseToken,
                    "price" to purchase.price.amount,
                    "currency" to purchase.price.currencyCode
                )
            )
        }
    }

    override suspend fun getAppInstanceId(): String? {
        return analytics.appInstanceId.await()
    }
}