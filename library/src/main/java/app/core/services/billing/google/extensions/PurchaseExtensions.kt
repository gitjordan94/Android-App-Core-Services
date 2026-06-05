package app.core.services.billing.google.extensions

import app.core.services.billing.model.ProductType
import app.core.services.billing.model.PurchaseDetails
import app.core.services.billing.model.PurchaseState
import com.android.billingclient.api.Purchase

internal fun Purchase.toInternal(productType: ProductType): PurchaseDetails {
    return PurchaseDetails(
        productIds = products,
        orderId = orderId,
        purchaseToken = purchaseToken,
        productType = productType,
        isAcknowledged = isAcknowledged,
        purchaseTime = purchaseTime,
        purchaseState = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.UNKNOWN
        },
    )
}