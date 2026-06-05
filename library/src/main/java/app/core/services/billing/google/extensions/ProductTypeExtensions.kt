package app.core.services.billing.google.extensions

import com.android.billingclient.api.BillingClient
import app.core.services.billing.model.ProductType

@BillingClient.ProductType
internal fun ProductType.toBillingProductType(): String {
    return when (this) {
        ProductType.ONE_TIME_PURCHASE -> BillingClient.ProductType.INAPP
        ProductType.SUBSCRIPTION -> BillingClient.ProductType.SUBS
    }
}