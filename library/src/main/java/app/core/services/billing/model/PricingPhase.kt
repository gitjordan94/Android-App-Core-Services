package app.core.services.billing.model

/**
 * Describes a single billing phase within a [SubscriptionOption].
 *
 * Each phase defines how the user is charged over a specific period of time,
 * including the price, recurrence behavior, and duration.
 *
 * Typical examples include:
 * - A free trial phase with zero cost.
 * - An introductory or promotional phase with a temporary discounted price.
 * - A recurring phase with the standard price.
 *
 * @property period The length of time covered by one billing cycle (e.g., monthly, yearly).
 * @property price The price charged for each billing cycle during this phase.
 * @property recurrenceMode Defines whether the phase repeats automatically or applies only once.
 * @property billingCycleCount The number of billing cycles for this phase, or `null` if it repeats indefinitely.
 */
data class PricingPhase(
    val period: Period,
    val price: Price,
    val recurrenceMode: RecurrenceMode,
    val billingCycleCount: Int?,
)