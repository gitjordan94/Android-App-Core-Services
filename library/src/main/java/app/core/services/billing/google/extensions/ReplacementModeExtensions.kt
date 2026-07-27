package app.core.services.billing.google.extensions

import app.core.services.billing.model.ReplacementMode
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode as BillingReplacementMode

@BillingReplacementMode
internal fun ReplacementMode.toBillingReplacementMode(): Int {
    return when (this) {
        ReplacementMode.WITH_TIME_PRORATION -> BillingReplacementMode.WITH_TIME_PRORATION
        ReplacementMode.CHARGE_PRORATED_PRICE -> BillingReplacementMode.CHARGE_PRORATED_PRICE
        ReplacementMode.WITHOUT_PRORATION -> BillingReplacementMode.WITHOUT_PRORATION
        ReplacementMode.DEFERRED -> BillingReplacementMode.DEFERRED
        ReplacementMode.CHARGE_FULL_PRICE -> BillingReplacementMode.CHARGE_FULL_PRICE
        ReplacementMode.KEEP_EXISTING -> BillingReplacementMode.KEEP_EXISTING
    }
}
