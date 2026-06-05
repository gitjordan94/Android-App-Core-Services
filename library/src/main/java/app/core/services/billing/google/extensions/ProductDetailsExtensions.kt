package app.core.services.billing.google.extensions

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import app.core.services.billing.model.BasePlan
import app.core.services.billing.model.Offer
import app.core.services.billing.model.Period
import app.core.services.billing.model.Price
import app.core.services.billing.model.PricingPhase
import app.core.services.billing.model.Product
import app.core.services.billing.model.ProductType
import app.core.services.billing.model.RecurrenceMode
import app.core.services.billing.model.SubscriptionOption
import app.core.services.billing.model.SubscriptionOptions

/**
 * Converts a Google Play [ProductDetails] object into the app's platform-agnostic [Product] model.
 *
 * This function handles the logic for both `SUBSCRIPTION` and `ONE_TIME_PURCHASE` types,
 * creating the appropriate data structure for each.
 *
 * @return A mapped [Product] object, or `null` if the essential details (like price) are missing.
 */
internal fun ProductDetails.toProduct(): Product? {
    return when (productType.toProductType()) {
        ProductType.SUBSCRIPTION -> createSubscriptionProduct()
        ProductType.ONE_TIME_PURCHASE -> createOneTimePurchaseProduct()
    }
}

/**
 * Helper function to create a [Product] for a subscription type.
 */
private fun ProductDetails.createSubscriptionProduct(): Product? {
    // 1. Map all available offers into our domain model.
    val subscriptionOptions = subscriptionOfferDetails
        ?.takeIf { it.isNotEmpty() }
        ?.map { it.toSubscriptionOption() }
        ?.let(::SubscriptionOptions)
        ?: return null // A subscription product must have offers.

    val basePlan = subscriptionOptions.basePlan ?: return null
    val price = basePlan.recurringPhase?.price ?: return null

    val details = Product.SubscriptionDetails(
        period = basePlan.billingPeriod,
        options = subscriptionOptions
    )

    return Product(
        id = productId,
        type = ProductType.SUBSCRIPTION,
        price = price,
        name = name,
        title = title,
        description = description,
        subscriptionDetails = details
    )
}

/**
 * Helper function to create a [Product] for a one-time purchase type.
 */
private fun ProductDetails.createOneTimePurchaseProduct(): Product? {
    // A one-time product must have a price.
    val price = oneTimePurchaseOfferDetails?.toPrice() ?: return null

    return Product(
        id = productId,
        type = ProductType.ONE_TIME_PURCHASE,
        price = price,
        name = name,
        title = title,
        description = description,
        subscriptionDetails = null
    )
}

/**
 * Maps a [ProductDetails.SubscriptionOfferDetails] to our sealed [SubscriptionOption] type.
 */
private fun ProductDetails.SubscriptionOfferDetails.toSubscriptionOption(): SubscriptionOption {
    val pricingPhases = this.pricingPhases.pricingPhaseList.map { phase ->
        PricingPhase(
            period = Period.parse(phase.billingPeriod),
            recurrenceMode = phase.recurrenceMode.toRecurrenceMode(),
            billingCycleCount = phase.billingCycleCount,
            price = phase.toPrice()
        )
    }

    return when (val offerId = this.offerId) {
        null -> BasePlan(
            id = this.basePlanId,
            basePlanId = this.basePlanId,
            pricingPhases = pricingPhases,
            tags = this.offerTags,
            offerToken = this.offerToken,
        )

        else -> Offer(
            id = offerId,
            basePlanId = this.basePlanId,
            offerId = offerId,
            pricingPhases = pricingPhases,
            tags = this.offerTags,
            offerToken = this.offerToken,
        )
    }
}

// region Helper Extension Functions

/**
 * Converts the pricing details of a one-time purchase offer to our [Price] model.
 */
private fun ProductDetails.OneTimePurchaseOfferDetails.toPrice(): Price {
    return Price(
        formatted = this.formattedPrice,
        amountMicros = this.priceAmountMicros,
        currencyCode = this.priceCurrencyCode,
    )
}

/**
 * Converts the pricing details of a subscription pricing phase to our [Price] model.
 */
private fun ProductDetails.PricingPhase.toPrice(): Price {
    return Price(
        formatted = this.formattedPrice,
        amountMicros = this.priceAmountMicros,
        currencyCode = this.priceCurrencyCode,
    )
}

/**
 * Maps the Google Play Billing product type string to our internal [ProductType].
 */
private fun @receiver:BillingClient.ProductType String?.toProductType(): ProductType {
    return when (this) {
        BillingClient.ProductType.SUBS -> ProductType.SUBSCRIPTION
        else -> ProductType.ONE_TIME_PURCHASE
    }
}

/**
 * Maps the Google Play Billing recurrence mode integer to our internal [RecurrenceMode].
 */
private fun @receiver:ProductDetails.RecurrenceMode Int.toRecurrenceMode(): RecurrenceMode {
    return when (this) {
        ProductDetails.RecurrenceMode.INFINITE_RECURRING -> RecurrenceMode.INFINITE_RECURRING
        ProductDetails.RecurrenceMode.FINITE_RECURRING -> RecurrenceMode.FINITE_RECURRING
        ProductDetails.RecurrenceMode.NON_RECURRING -> RecurrenceMode.NON_RECURRING
        else -> RecurrenceMode.UNKNOWN
    }
}