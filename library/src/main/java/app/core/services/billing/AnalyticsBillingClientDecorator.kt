package app.core.services.billing

import android.app.Activity
import com.appsflyer.AFInAppEventType.INITIATED_CHECKOUT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.analytics.amplitude.AmplitudeAnalytics
import app.core.services.analytics.firebase.FirebaseAnalytics
import app.core.services.core.AnalyticsEvents.PURCHASE_ERROR
import app.core.services.core.AnalyticsProperties.ACTIVE_SUBS
import app.core.services.core.AnalyticsProperties.ALL_PURCHASED_PRODUCT_IDS
import app.core.services.billing.model.Purchases
import app.core.services.billing.model.Purchase

/**
 * A Decorator for the [BillingClient] interface that adds analytics logging
 * to purchase flows and entitlement updates.
 */
internal class AnalyticsBillingClientDecorator(
    private val decorated: BillingClient,
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val amplitudeAnalytics: AmplitudeAnalytics,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BillingClient by decorated {
    init {
        decorated.getPurchasesFlow()
            .onEach(::setUserProperties)
            .launchIn(coroutineScope)
    }

    override suspend fun purchase(
        activity: Activity,
        productId: String
    ): Purchase {
        return purchaseWithAnalytics {
            decorated.purchase(activity, productId)
        }
    }

    override suspend fun purchase(activity: Activity, request: PurchaseRequest): Purchase {
        return purchaseWithAnalytics {
            decorated.purchase(activity, request)
        }
    }

    private suspend fun purchaseWithAnalytics(
        purchaseBlock: suspend () -> Purchase
    ): Purchase {
        appsFlyerAnalytics.logEvent(INITIATED_CHECKOUT)

        return try {
            val purchase = purchaseBlock()

            appsFlyerAnalytics.logPurchase(purchase)
            amplitudeAnalytics.logPurchase(purchase)
            firebaseAnalytics.logPurchase(purchase)

            purchase
        } catch (exception: BillingClientException) {
            if (exception.error != BillingError.PurchaseCancelledError) {
                amplitudeAnalytics.logEvent(PURCHASE_ERROR)
            }

            throw exception
        }
    }

    private fun setUserProperties(purchases: Purchases?) {
        if (purchases == null) {
            return
        }

        amplitudeAnalytics.setUserProperties(
            mapOf(
                ACTIVE_SUBS to purchases.activeSubscriptions.toTypedArray(),
                ALL_PURCHASED_PRODUCT_IDS to purchases.allPurchasedProductIds.toTypedArray(),
            )
        )
    }
}