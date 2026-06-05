package app.core.services.billing.model

/**
 * Represents the details of a purchasable product, whether it's a one-time purchase or a subscription.
 *
 * This class is a platform-agnostic representation of a product's listing details, abstracting
 * away platform-specific objects to provide a clean and consistent interface.
 *
 * @property id The unique product identifier defined in the store's configuration (e.g., "premium_upgrade").
 * @property type The type of product, distinguishing between a [ProductType.ONE_TIME_PURCHASE] and a [ProductType.SUBSCRIPTION].
 * @property name The product's developer-specified name (e.g., "Premium Upgrade").
 * @property title The formatted title for display, which may include the app's name (e.g., "Premium Upgrade (My App Name)").
 * @property description A detailed description of the product.
 * @property price The price information for the product. For a subscription, this typically represents the price of the default base plan.
 * @property subscriptionDetails Contains details specific to subscription products, such as the billing period and available offers. This will be `null` for one-time products.
 */
data class Product(
    val id: String,
    val type: ProductType,
    val name: String,
    val title: String,
    val description: String,
    val price: Price,
    val subscriptionDetails: SubscriptionDetails?,
) {
    /**
     * Contains details specific to subscription products.
     *
     * @property period The billing period of the subscription's base plan.
     * @property options A specialized collection of all available offers for this subscription, including the base plan and any special offers.
     */
    data class SubscriptionDetails(
        val period: Period?,
        val options: SubscriptionOptions
    ) {
        /**
         * The default subscription option that will be applied if a purchase is made without
         * specifying a different one. This is typically the [BasePlan].
         */
        val defaultOption: SubscriptionOption?
            get() = options.basePlan
    }
}
