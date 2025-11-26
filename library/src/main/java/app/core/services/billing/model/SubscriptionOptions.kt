package app.core.services.billing.model

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
data class SubscriptionOptions(
    private val subscriptionOptions: List<SubscriptionOption>
) : List<SubscriptionOption> by subscriptionOptions {
    /**
     * The standard [BasePlan] from the list of options.
     * There is typically only one base plan per product.
     */
    val basePlan: BasePlan?
        get() = this.filterIsInstance<BasePlan>().firstOrNull()

    /**
     * The first special [Offer] in the list that includes a free trial period.
     */
    val freeTrial: SubscriptionOption?
        get() = this.filterIsInstance<Offer>().firstOrNull { it.trialPhase != null }

    /**
     * The first special [Offer] in the list that includes an introductory price.
     * An offer can have both a free trial and an introductory price.
     */
    val introOffer: SubscriptionOption?
        get() = this.filterIsInstance<Offer>().firstOrNull { it.promoPhase != null }
}