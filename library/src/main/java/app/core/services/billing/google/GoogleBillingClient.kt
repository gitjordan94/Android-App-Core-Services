package app.core.services.billing.google

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.core.services.billing.BillingClient
import app.core.services.billing.BillingClientException
import app.core.services.billing.BillingError
import app.core.services.billing.PurchaseRequest
import app.core.services.billing.db.BillingDatabase
import app.core.services.billing.db.entity.toPurchaseData
import app.core.services.billing.google.error.BillingException
import app.core.services.billing.google.extensions.toBillingProductType
import app.core.services.billing.google.extensions.toBillingReplacementMode
import app.core.services.billing.google.extensions.toInternal
import app.core.services.billing.google.extensions.toProduct
import app.core.services.billing.model.Product
import app.core.services.billing.model.ProductType
import app.core.services.billing.model.Purchase
import app.core.services.billing.model.PurchaseDetails
import app.core.services.billing.model.Purchases
import app.core.services.billing.model.ReplacementMode
import com.android.billingclient.api.BillingClient.ProductType.INAPP
import com.android.billingclient.api.BillingClient.ProductType.SUBS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The default implementation of [BillingClient] for the Google Play Store.
 *
 * This class orchestrates all interactions with the Google Play Billing Library,
 * handling connection management, purchase flows, and data fetching.
 */
internal class GoogleBillingClient @Inject constructor(
    private val billingClientWrapper: BillingClientWrapper,
    private val billingDatabase: BillingDatabase,
    private val obfuscatedUserIdProvider: ObfuscatedUserIdProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BillingClient {

    private companion object {
        private const val TAG = "GoogleBillingClient"
        private const val RETRY_COUNT = 3
        private const val OPERATION_TIMEOUT_MS = 30_000L
        private const val CONNECTION_RETRY_DELAY_MS = 2_000L
    }

    private val fetchMutex = Mutex()

    @Volatile
    private var fetchJob: Job? = null

    private val isInitialized = AtomicBoolean(false)

    init {
        billingClientWrapper.setOnPurchasesUpdatedListener(this::onPurchasesUpdated)

        monitorConnectionState()
        observeAppLifecycle()
        performInitialSetup()
    }

    private fun monitorConnectionState() {
        billingClientWrapper.connectionState
            .onEach { state ->
                Timber.tag(TAG).d("Connection state changed: $state")

                when (state) {
                    is BillingConnectionState.Connected -> {
                        if (!isInitialized.get()) {
                            performInitialFetch()
                        }
                    }

                    is BillingConnectionState.Error -> {
                        Timber.tag(TAG).e(state.e, "Billing connection error")
                    }

                    BillingConnectionState.Disconnected -> {
                        Timber.tag(TAG).w("Billing disconnected")
                    }

                    BillingConnectionState.Connecting -> {
                        Timber.tag(TAG).d("Billing connecting")
                    }

                }
            }
            .catch { e ->
                Timber.tag(TAG).e(e, "Error monitoring connection state")
            }
            .launchIn(coroutineScope)
    }

    private fun observeAppLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    super.onStart(owner)
                    if (isInitialized.get()) {
                        coroutineScope.launch {
                            try {
                                loadPurchases()
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Error refreshing on foreground")
                            }
                        }
                    }
                }
            }
        )
    }

    private fun performInitialSetup() {
        coroutineScope.launch {
            try {
                performInitialFetch()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in initial setup")
            }
        }
    }

    private fun performInitialFetch() {
        if (isInitialized.compareAndSet(false, true)) {
            loadPurchases()
        }
    }

    private fun loadPurchases() {
        coroutineScope.launch {
            fetchMutex.withLock {
                fetchJob?.cancel()

                fetchJob = launch {
                    try {
                        fetchPurchasesWithRetry()
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to refresh purchases")
                    }
                }
            }
        }
    }

    private suspend fun fetchPurchasesWithRetry(maxRetries: Int = RETRY_COUNT): Purchases {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return fetchPurchases()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delay = CONNECTION_RETRY_DELAY_MS * (attempt + 1)
                    Timber.tag(TAG).w("Fetch attempt ${attempt + 1} failed, retrying in ${delay}ms")
                    delay(delay.milliseconds)
                }
            }
        }

        throw lastException ?: BillingClientException(BillingError.UnknownError)
    }

    override suspend fun getStoreCountry(): String? =
        withTimeout(OPERATION_TIMEOUT_MS.milliseconds) {
            try {
                billingClientWrapper.getBillingConfig()?.countryCode
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error getting store country")
                null
            }
        }

    override suspend fun getPurchases(): Purchases {
        return try {
            fetchPurchasesWithRetry()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching purchases, returning cached")
            Purchases(billingDatabase.purchasesDao.getAllPurchases().map { it.toPurchaseData() })
        }
    }

    override fun getPurchasesFlow(): Flow<Purchases> {
        return billingDatabase.purchasesDao.getAllPurchasesFlow()
            .map { entities -> Purchases(entities.map { it.toPurchaseData() }) }
            .catch { e ->
                Timber.tag(TAG).e(e, "Error fetching purchases flow")
                emit(Purchases(emptyList()))
            }
    }

    override suspend fun purchase(activity: Activity, productId: String): Purchase {
        return purchase(activity, productId, offerToken = null)
    }

    override suspend fun purchase(activity: Activity, request: PurchaseRequest): Purchase {
        return when (request) {
            is PurchaseRequest.InApp -> purchase(
                activity = activity,
                productId = request.productId
            )

            is PurchaseRequest.Subscription -> purchase(
                activity = activity,
                productId = request.productId,
                offerToken = request.offerToken,
                oldProductId = request.replacement?.oldProductId,
                replacementMode = request.replacement?.replacementMode,
                oldPurchaseToken = request.replacement?.oldPurchaseToken
            )
        }
    }

    override suspend fun showInAppMessages(activity: Activity) {
        try {
            billingClientWrapper.showInAppMessages(activity)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error showing subscription recovery messages")
            throw e.toPurchasesException()
        }
    }

    private suspend fun purchase(
        activity: Activity,
        productId: String,
        offerToken: String? = null,
        oldProductId: String? = null,
        oldPurchaseToken: String? = null,
        replacementMode: ReplacementMode? = null,
    ): Purchase {
        val product = try {
            getProducts(
                productIds = listOf(productId),
                type = if (offerToken != null) ProductType.SUBSCRIPTION else null
            ).firstOrNull() ?: throw BillingClientException(
                BillingError.ProductNotAvailableForPurchaseError,
                message = "Product $productId not found"
            )
        } catch (e: Exception) {
            Timber.e("Error fetching product $productId")
            throw e.toPurchasesException()
        }

        val subscriptionOption = offerToken?.let {
            product.subscriptionDetails?.options?.firstOrNull { it.offerToken == offerToken }
        } ?: product.subscriptionDetails?.options?.firstOrNull()

        val purchase = try {
            billingClientWrapper.launchBillingFlow(
                activity = activity,
                productId = productId,
                productType = product.type.toBillingProductType(),
                selectedOfferToken = subscriptionOption?.offerToken,
                oldProductId = oldProductId,
                oldPurchaseToken = oldPurchaseToken,
                replacementMode = replacementMode?.toBillingReplacementMode(),
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Purchase flow failed for product $productId")
            throw e.toPurchasesException()
        }

        coroutineScope.launch {
            try {
                loadPurchases()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error refreshing after purchase")
            }
        }

        return Purchase(
            product = product,
            purchaseToken = purchase.purchaseToken,
            subscriptionOptionId = subscriptionOption?.id
        )
    }

    override suspend fun restorePurchases(): Purchases {
        return fetchPurchasesWithRetry()
    }

    private suspend fun fetchPurchases(): Purchases {
        return withTimeout(OPERATION_TIMEOUT_MS.milliseconds) {
            try {
                val purchaseDataList = fetchAllPurchaseData()
                val purchases = Purchases(purchaseDataList)

                billingDatabase.purchasesDao.upsertAll(purchaseDataList)

                Timber.tag(TAG)
                    .d("Purchases updated: ${purchases.activeSubscriptions.size} subs, ${purchases.allPurchasedProductIds.size} total")

                purchases
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error fetching purchases")
                throw e.toPurchasesException()
            }
        }
    }

    private suspend fun fetchAllPurchaseData(): List<PurchaseDetails> {
        return coroutineScope.async {
            val subsDeferred = async {
                try {
                    billingClientWrapper.queryPurchases(SUBS)
                        .map { it.toInternal(ProductType.SUBSCRIPTION, obfuscatedUserIdProvider) }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error fetching subscription purchases")
                    emptyList()
                }
            }

            val inAppDeferred = async {
                try {
                    billingClientWrapper.queryPurchases(INAPP)
                        .map {
                            it.toInternal(
                                ProductType.ONE_TIME_PURCHASE,
                                obfuscatedUserIdProvider
                            )
                        }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error fetching in-app purchases")
                    emptyList()
                }
            }

            subsDeferred.await() + inAppDeferred.await()
        }.await()
    }

    override suspend fun getProducts(
        productIds: List<String>,
        type: ProductType?,
    ): List<Product> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return withTimeout(OPERATION_TIMEOUT_MS.milliseconds) {
            val types = type?.let { setOf(it) } ?: setOf(
                ProductType.ONE_TIME_PURCHASE,
                ProductType.SUBSCRIPTION
            )
            val result = mutableListOf<Product>()

            for (productType in types) {
                try {
                    val products =
                        billingClientWrapper.getProducts(
                            productIds,
                            productType.toBillingProductType()
                        )
                            .mapNotNull { product -> product.toProduct() }

                    result.addAll(products)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error fetching products for type $productType")
                    throw e.toPurchasesException()
                }
            }

            result.sortedBy { product ->
                productIds.indexOf(product.id).takeIf { it != -1 } ?: Int.MAX_VALUE
            }
        }
    }

    private fun onPurchasesUpdated(purchases: List<com.android.billingclient.api.Purchase>?) {
        Timber.tag(TAG).d("Purchases updated: ${purchases?.size ?: 0} items")

        coroutineScope.launch {
            try {
                // Small delay to allow Google Play to process
                delay(500.milliseconds)
                loadPurchases()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error handling purchase update")
            }
        }
    }

    /**
     * Clean shutdown - should be called when the app is being destroyed
     */
    suspend fun shutdown() {
        try {
            fetchJob?.cancel()
            billingClientWrapper.disconnect()
            Timber.tag(TAG).d("GooglePurchases shutdown complete")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during shutdown")
        }
    }

    private fun Throwable.toPurchasesException(): BillingClientException {
        return when (this) {
            is BillingClientException -> this

            is BillingException.ItemAlreadyOwnedException -> {
                BillingClientException(BillingError.ProductAlreadyPurchasedError, message)
            }

            is BillingException.ServiceDisconnectedException,
            is BillingException.ServiceUnavailableException,
            is BillingException.NetworkErrorException -> {
                BillingClientException(BillingError.NetworkError, message)
            }

            is BillingException.UserCanceledException -> {
                BillingClientException(BillingError.PurchaseCancelledError, message)
            }

            is BillingException.DeveloperErrorException -> {
                BillingClientException(BillingError.DeveloperError, message)
            }

            is BillingException.BillingUnavailableException -> {
                BillingClientException(BillingError.ServiceUnavailableError, message)
            }

            is TimeoutCancellationException -> {
                BillingClientException(BillingError.NetworkError, "Operation timed out")
            }

            is BillingException -> {
                BillingClientException(BillingError.UnknownError, message ?: "Billing error")
            }

            else -> {
                BillingClientException(
                    BillingError.UnknownError,
                    message ?: "Unknown error: ${this::class.simpleName}"
                )
            }
        }
    }
}