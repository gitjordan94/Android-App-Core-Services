package app.core.services.billing.model

/**
 * Represents a specific way a user can purchase a subscription, which can be either a
 * standard [BasePlan] or a [Offer] (a special offer).
 *
 * This sealed interface provides a type-safe way to handle different subscription options,
 * ensuring that all possible configurations are handled explicitly.
 */
sealed interface SubscriptionOption {
    /**
     * A stable, unique identifier for this specific option.
     * - For a [BasePlan], this is the same as the [basePlanId].
     * - For an [Offer], this is the `offerId`.
     */
    val id: String

    /**
     * The identifier of the base plan this option is associated with.
     * All offers are linked to a parent base plan.
     */
    val basePlanId: String

    /**
     * An ordered list of [PricingPhase]s that detail how a user is charged over time.
     * This always ends with the recurring price phase.
     */
    val pricingPhases: List<PricingPhase>

    /**
     * A list of custom, developer-defined tags associated with this option,
     * configured in the store's commerce portal.
     */
    val tags: List<String>

    /**
     * The token required by the underlying billing system to initiate the
     * purchase of this specific subscription option.
     */
    val offerToken: String

    /**
     * The recurring (full price) [PricingPhase] that applies after all introductory
     * phases (if any) are complete.
     */
    val recurringPhase: PricingPhase?
        get() = pricingPhases.lastOrNull()

    /**
     * The billing period of the final, recurring phase of the subscription.
     */
    val billingPeriod: Period?
        get() = recurringPhase?.period
}

/**
 * A standard subscription plan without any special offers. It is the default
 * offering for a subscription product.
 *
 * @property id The stable identifier of this plan, which is the same as [basePlanId].
 * @property basePlanId The base plan ID configured in the commerce portal.
 * @property pricingPhases An ordered list of pricing phases. For a base plan, this
 *           typically contains only one phase: the recurring one.
 * @property tags A list of custom, developer-defined tags.
 * @property offerToken The token required to purchase this base plan.
 */
data class BasePlan(
    override val id: String,
    override val basePlanId: String,
    override val pricingPhases: List<PricingPhase>,
    override val tags: List<String>,
    override val offerToken: String
) : SubscriptionOption

/**
 * A special offer for a base plan, which may include a free trial or an introductory price.
 *
 * @property id The stable identifier of this offer, which is the same as [offerId].
 * @property basePlanId The identifier of the base plan this offer is for.
 * @property offerId The unique identifier for this special offer.
 * @property pricingPhases An ordered list of pricing phases, starting with any
 *           introductory phases and ending with the recurring one.
 * @property tags A list of custom, developer-defined tags.
 * @property offerToken The token required to purchase this specific offer.
 */
data class Offer(
    override val id: String,
    override val basePlanId: String,
    val offerId: String,
    override val pricingPhases: List<PricingPhase>,
    override val tags: List<String>,
    override val offerToken: String,
) : SubscriptionOption {
    /**
     * The introductory phases of this special offer, which occur before the final, recurring phase.
     */
    private val introductoryPhases: List<PricingPhase>
        get() = pricingPhases.dropLast(1)

    /**
     * The free trial [PricingPhase], if one is available for this offer.
     * A trial is defined as any introductory phase with a price of zero.
     */
    val trialPhase: PricingPhase?
        get() = introductoryPhases.firstOrNull { it.price.amountMicros == 0L }

    /**
     * The introductory or discounted price [PricingPhase], if one is available.
     * This is defined as any introductory phase with a price greater than zero.
     */
    val promoPhase: PricingPhase?
        get() = introductoryPhases.firstOrNull { it.price.amountMicros > 0L }
}