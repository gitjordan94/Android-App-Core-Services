package app.core.services.firebase

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvent
import app.core.services.analytics.PurchaseEventLogger
import app.core.services.common.toBundle
import app.core.services.billing.model.Purchase
import kotlinx.coroutines.tasks.await

internal class FirebaseAnalytics : Analytics, PurchaseEventLogger {
    private val analytics = Firebase.analytics

    internal fun setUserId(userId: String?) {
        analytics.setUserId(userId)
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
            logEvent("trial_started")
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

    internal suspend fun getAppInstanceId(): String {
        return analytics.appInstanceId.await()
    }
}