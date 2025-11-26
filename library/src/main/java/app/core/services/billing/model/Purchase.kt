package app.core.services.billing.model

/**
 * Represents an in-app billing purchase.
 */
data class Purchase(
    /**
     * Product purchased.
     */
    val product: Product,

    /**
     * Token that uniquely identifies a purchase.
     */
    val purchaseToken: String,

    /**
     * The id of the SubscriptionOption purchased.
     * In Google, this will be calculated from the basePlanId and offerId
     * Null for restored transactions and purchases initiated outside of the app.
     */
    val subscriptionOptionId: String?,
) {

    val price: Price
        get() {
            return when (product.type) {
                ProductType.SUBSCRIPTION -> {
                    product.subscriptionDetails?.options?.firstOrNull { it.id == subscriptionOptionId }
                        ?.pricingPhases
                        ?.firstOrNull()
                        ?.price
                        ?: product.price
                }

                ProductType.ONE_TIME_PURCHASE -> product.price
            }
        }
}