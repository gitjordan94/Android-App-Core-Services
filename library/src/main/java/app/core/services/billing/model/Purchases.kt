package app.core.services.billing.model

/**
 * @property activeSubscriptions active subscription productIds.
 * @property allPurchasedProductIds purchased productIds, active and inactive.
 */
data class Purchases(
    val activeSubscriptions: Set<String>,
    val allPurchasedProductIds: Set<String>
) {
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