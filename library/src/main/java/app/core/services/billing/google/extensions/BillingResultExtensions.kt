package app.core.services.billing.google.extensions

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult

/**
 * Returns a formatted string representation of the [BillingResult].
 *
 * This property constructs a message combining the debug message and the
 * response code name. It's intended for logging and debugging purposes to
 * provide a human-readable explanation of a billing operation's outcome.
 *
 * @property message A formatted string containing the debug message and response code name.
 *                  Format: "Message: [debugMessage]. Response Code: [responseCodeName]"
 *                  Example : "Message: Item already owned. Response Code: ITEM_ALREADY_OWNED"
 * @return A String representing the billing result.
 */
internal val BillingResult.message: String
    get() = buildString {
        if (responseCode != BillingResponseCode.OK) {
            append("Debug Message: $debugMessage, ")
        }

        append("Response Code: $responseCodeName")
    }

/**
 * Gets the name of the `BillingResult`'s response code (e.g., "OK", "USER_CANCELED").
 *
 * Returns the constant name if found in `BillingResponseCode`, otherwise returns a string representation of the `BillingResult`.
 * Useful for debugging.
 *
 * @receiver The BillingResult object.
 * @return The name of the response code or a string representation of the BillingResult.
 */
private val BillingResult.responseCodeName: String
    get() {
        val allPossibleBillingResponseCodes = BillingResponseCode::class.java.declaredFields
        return allPossibleBillingResponseCodes
            .firstOrNull { it.getInt(it) == this.responseCode }
            ?.name
            ?: "$this"
    }