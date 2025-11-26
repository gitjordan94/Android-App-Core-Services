package app.core.services.billing.model

/**
 * A data class representing the price of a product or subscription offer.
 *
 * This class encapsulates pricing details provided by the Google Play Billing Library.
 *
 * @property formatted The price formatted for display to the user, including the currency symbol (e.g., "$9.99").
 * @property amountMicros The price in micro-units, where 1,000,000 micro-units equal one unit of the currency.
 * @property currencyCode The ISO 4217 currency code for the price (e.g., "USD", "EUR").
 */
data class Price(
    val formatted: String,
    val amountMicros: Long,
    val currencyCode: String,
) {
    /**
     * The price amount as a `Double`, converted from micro-units.
     *
     * This is a convenience property for calculations. For example, if `amountMicros` is `9990000`,
     * this will return `9.99`.
     */
    val amount: Double
        get() = amountMicros / 1_000_000.0
}