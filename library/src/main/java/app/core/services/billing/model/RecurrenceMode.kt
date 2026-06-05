package app.core.services.billing.model

/**
 * Recurrence mode for a pricing phase
 */
enum class RecurrenceMode {
    /** The pricing phase repeats indefinitely until the subscription is cancelled. */
    INFINITE_RECURRING,

    /** The pricing phase repeats for a fixed number of billing periods. */
    FINITE_RECURRING,

    /** The pricing phase is a one-time charge and does not repeat. */
    NON_RECURRING,

    /** The recurrence mode is unknown or unsupported. */
    UNKNOWN;
}