package app.core.services.billing.model

/**
 * Replacement mode for subscription upgrade/downgrade flows.
 *
 * Mirrors `BillingFlowParams.SubscriptionUpdateParams.ReplacementMode` from the
 * Google Play Billing Library, exposed here to keep callers store-agnostic.
 */
enum class ReplacementMode {
    /**
     * Old plan stays in effect until its expiration, then the new plan takes over.
     * No immediate charge.
     */
    WITH_TIME_PRORATION,

    /**
     * New plan takes effect immediately; remaining value of the old plan is credited
     * as prorated time on the new plan.
     */
    CHARGE_PRORATED_PRICE,

    /**
     * New plan takes effect immediately with no proration; the user is not charged
     * or refunded for the change until the next billing cycle.
     */
    WITHOUT_PRORATION,

    /**
     * Old plan continues to be billed for the current period; new plan takes effect
     * only at the next renewal.
     */
    DEFERRED,

    /**
     * New plan takes effect immediately and the user is charged the full price of the
     * new plan without any credit for the unused portion of the old plan.
     */
    CHARGE_FULL_PRICE,

    KEEP_EXISTING,
}