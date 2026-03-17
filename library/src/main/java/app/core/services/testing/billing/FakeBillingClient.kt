package app.core.services.testing.billing

import android.app.Activity
import app.core.services.billing.BillingClient
import app.core.services.billing.PurchaseRequest
import app.core.services.billing.model.Product
import app.core.services.billing.model.ProductType
import app.core.services.billing.model.Purchase
import app.core.services.billing.model.Purchases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class FakeBillingClient(
    var storeCountry: String? = null,
    var products: List<Product> = emptyList(),
    var purchases: Purchases = Purchases(
        activeSubscriptions = emptySet(),
        allPurchasedProductIds = emptySet()
    )
) : BillingClient {
    override suspend fun getStoreCountry() = storeCountry

    override suspend fun getPurchases() = purchases

    override fun getPurchasesFlow(): Flow<Purchases> {
        return flowOf(purchases)
    }

    override suspend fun getProducts(
        productIds: List<String>,
        type: ProductType?
    ): List<Product> {
        return products.filter { it.id in productIds }
    }

    override suspend fun purchase(
        activity: Activity,
        productId: String
    ): Purchase {
        val product = products.first { it.id == productId }

        return Purchase(
            product = product,
            purchaseToken = UUID.randomUUID().toString(),
            subscriptionOptionId = product.subscriptionDetails?.defaultOption?.id
        )
    }

    override suspend fun purchase(
        activity: Activity,
        request: PurchaseRequest
    ) = purchase(activity, request.productId)

    override suspend fun showInAppMessages(activity: Activity) {
        // No-op
    }

    override suspend fun restorePurchases() = purchases
}