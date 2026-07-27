package app.core.services.billing

import app.core.services.billing.model.ReplacementMode

sealed class PurchaseRequest {
    abstract val productId: String

    data class InApp(override val productId: String) : PurchaseRequest()

    data class Subscription(
        override val productId: String,
        val offerToken: String? = null,
        val replacement: Replacement? = null,
    ) : PurchaseRequest() {

        data class Replacement(
            val oldProductId: String,
            val oldPurchaseToken: String,
            val replacementMode: ReplacementMode
        )
    }
}