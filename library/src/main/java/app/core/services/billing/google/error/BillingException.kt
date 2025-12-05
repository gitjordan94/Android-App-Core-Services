package app.core.services.billing.google.error

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult

/**
 * Represents exceptions that can occur during billing operations.
 */
internal sealed class BillingException(message: String) : Exception(message) {
    /**
     * Transaction was canceled by the user.
     */
    class UserCanceledException(message: String) : BillingException(message)

    /**
     * Item is already owned by the user.
     */
    class ItemAlreadyOwnedException(message: String) : BillingException(message)

    /**
     * The service is currently unavailable.
     */
    class ServiceUnavailableException(message: String) : BillingException(message)

    /**
     * The billing service is not available on the device (e.g., the Play Store app is outdated or not set up).
     */
    class BillingUnavailableException(message: String) : BillingException(message)

    /**
     *  A network error occurred during the operation.
     */
    class NetworkErrorException(message: String) : BillingException(message)

    /**
     * The app is not connected to the Play Store service via the Google Play Billing Library.
     */
    class ServiceDisconnectedException(message: String) : BillingException(message)

    /**
     * Fatal error during the API action.
     */
    class Error(message: String) : BillingException(message)

    /**
     * Error resulting from incorrect usage of the API.
     */
    class DeveloperErrorException(message: String) : BillingException(message)

    companion object {
        /**
         * Creates a [BillingException] from a [BillingResult].
         *
         * @param billingResult The [BillingResult] to convert.
         * @return The corresponding [BillingException].
         */
        fun from(billingResult: BillingResult): BillingException {
            val message = billingResult.debugMessage

            return when (billingResult.responseCode) {
                BillingResponseCode.USER_CANCELED -> UserCanceledException(message)
                BillingResponseCode.SERVICE_UNAVAILABLE -> ServiceUnavailableException(message)
                BillingResponseCode.BILLING_UNAVAILABLE -> BillingUnavailableException(message)
                BillingResponseCode.ITEM_ALREADY_OWNED -> ItemAlreadyOwnedException(message)
                BillingResponseCode.SERVICE_DISCONNECTED -> ServiceDisconnectedException(message)
                BillingResponseCode.NETWORK_ERROR -> NetworkErrorException(message)
                BillingResponseCode.DEVELOPER_ERROR -> DeveloperErrorException(message)
                else -> Error(message)
            }
        }
    }
}