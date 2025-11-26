package app.core.services.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import app.core.services.billing.model.Purchases
import app.core.services.billing.model.Product
import app.core.services.billing.model.ProductType
import app.core.services.billing.model.Purchase

interface BillingClient {
    /**
     * Retrieves the two-letter country code of the user's store account.
     *
     * @return A nullable String containing the ISO 3166-1 alpha-2 country code (e.g., "US").
     *         Returns `null` if the store country cannot be determined.
     * @throws BillingClientException if there is an error communicating with the store.
     */
    @Throws(BillingClientException::class)
    suspend fun getStoreCountry(): String?

    /**
     * Fetches the most up-to-date entitlements from the server.
     * @return The latest [Purchases] for the current user.
     */
    @Throws(BillingClientException::class)
    suspend fun getPurchases(): Purchases

    /**
     * Provides a stream of the latest cached entitlements.
     * @return A [Flow] that emits the latest cached [Purchases].
     */
    fun getPurchasesFlow(): Flow<Purchases>

    /**
     * Gets the product(s) for the given list of product ids.
     *
     * @param [productIds] List of productIds
     *
     * @return A list of [Product] with the products that have been able to be fetched from the store successfully.
     * Not found products will be ignored.
     */
    @Throws(BillingClientException::class)
    suspend fun getProducts(productIds: List<String>, type: ProductType? = null): List<Product>

    /**
     * Initiates a purchase flow for a product using its ID.
     *
     * This is a convenience method that assumes the default offer for a product.
     * For purchasing specific offers (e.g., promotional trials), use the overload
     * that accepts a [PurchaseRequest].
     *
     * @param activity The current foreground [Activity] required to launch the purchase flow.
     * @param productId The unique identifier of the product to purchase.
     * @return The resulting [Purchase] object upon a successful transaction.
     * @throws BillingClientException if the purchase fails, is cancelled, or the product is not found.
     */
    @Throws(BillingClientException::class)
    suspend fun purchase(activity: Activity, productId: String): Purchase

    /**
     * Initiates a purchase flow for a specific product or offer.
     *
     * @param activity The current foreground [Activity].
     * @param request The [PurchaseRequest] containing the Activity and product details.
     * @return The resulting [Purchase] object upon a successful transaction.
     * @throws BillingClientException if the purchase fails or is cancelled by the user.
     */
    @Throws(BillingClientException::class)
    suspend fun purchase(activity: Activity, request: PurchaseRequest): Purchase

    /**
     * Displays any available in-app messages from the store, such as payment-related notifications.
     *
     * @param activity The current foreground [Activity].
     * @throws BillingClientException if there is an error showing the message.
     */
    @Throws(BillingClientException::class)
    suspend fun showInAppMessages(activity: Activity)

    /**
     * Restores purchases made by the current user.
     * @return The updated [Purchases] after the restore is complete.
     */
    @Throws(BillingClientException::class)
    suspend fun restorePurchases(): Purchases
}