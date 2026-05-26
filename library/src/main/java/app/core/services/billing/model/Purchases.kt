package app.core.services.billing.model

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
data class Purchases(
    val purchases: List<PurchaseDetails> = emptyList()
) : List<PurchaseDetails> by purchases {
    val activeSubscriptions: Set<String>
        get() = purchases
            .filter { it.productType == ProductType.SUBSCRIPTION }
            .flatMap { it.productIds }
            .toSet()

    val allPurchasedProductIds: Set<String>
        get() = purchases.flatMap { it.productIds }
            .toSet()

    /**
     * Checks if the user has an active subscription for a specific product.
     *
     * @param productId The product identifier to check.
     * @return `true` if there is an active subscription for the given product ID, `false` otherwise.
     */
    fun hasActiveSubscription(productId: String): Boolean {
        return productId in activeSubscriptions
    }

    /**
     * Checks if the user has ever purchased a specific product (active or not).
     *
     * This is useful for checking ownership of one-time products or for "win-back" campaigns
     * targeted at users with expired subscriptions.
     *
     * @param productId The product identifier to check.
     * @return `true` if a purchase record exists for the given product ID, `false` otherwise.
     */
    fun hasPurchase(productId: String): Boolean {
        return productId in allPurchasedProductIds
    }
}