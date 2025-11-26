package app.core.services.billing

sealed class PurchaseRequest {
    abstract val productId: String

    data class InApp(override val productId: String) : PurchaseRequest()

    data class Subscription(
        override val productId: String,
        val offerToken: String? = null
    ) : PurchaseRequest()
}