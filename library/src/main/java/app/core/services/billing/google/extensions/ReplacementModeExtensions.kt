package app.core.services.billing.google.extensions

import app.core.services.billing.model.ReplacementMode
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams

@SubscriptionUpdateParams.ReplacementMode
internal fun ReplacementMode.toBillingReplacementMode(): Int {
    return when (this) {
        ReplacementMode.WITH_TIME_PRORATION -> SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
        ReplacementMode.CHARGE_PRORATED_PRICE -> SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
        ReplacementMode.WITHOUT_PRORATION -> SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION
        ReplacementMode.DEFERRED -> SubscriptionUpdateParams.ReplacementMode.DEFERRED
        ReplacementMode.CHARGE_FULL_PRICE -> SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE
    }
}
