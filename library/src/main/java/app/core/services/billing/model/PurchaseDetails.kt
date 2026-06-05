package app.core.services.billing.model

data class PurchaseDetails(
    val productIds: List<String>,
    val orderId: String?,
    val purchaseToken: String,
    val productType: ProductType,
    val isAcknowledged: Boolean,
    val purchaseTime: Long,
    val purchaseState: PurchaseState
)