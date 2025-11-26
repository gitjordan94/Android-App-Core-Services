package app.core.services.billing.google

import android.app.Activity
import android.content.Context
import androidx.annotation.UiThread
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingConfig
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageParams.InAppMessageCategoryId
import com.android.billingclient.api.InAppMessageResult.InAppMessageResponseCode
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchaseHistory
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import app.core.services.billing.google.error.BillingException
import app.core.services.billing.google.extensions.message
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

internal interface Billing {
    val connectionState: StateFlow<BillingConnectionState>

    @Throws(BillingException::class)
    suspend fun connect(): BillingConnectionState

    suspend fun disconnect()

    fun setOnPurchasesUpdatedListener(listener: OnPurchasesUpdatedListener)

    @Throws(BillingException::class)
    suspend fun getProducts(
        productIds: List<String>,
        @ProductType productType: String
    ): List<ProductDetails>

    @Throws(BillingException::class)
    suspend fun queryPurchases(@ProductType productType: String): List<Purchase>

    @Throws(BillingException::class)
    suspend fun queryAllPurchases(): List<Purchase>

    @Throws(BillingException::class)
    suspend fun queryPurchaseHistory(@ProductType productType: String): List<PurchaseHistoryRecord>

    @Throws(BillingException::class)
    suspend fun getBillingConfig(): BillingConfig?

    @Throws(BillingException::class)
    suspend fun showInAppMessages(activity: Activity)

    @Throws(BillingException::class)
    suspend fun launchBillingFlow(
        activity: Activity,
        productId: String,
        @ProductType productType: String,
        selectedOfferToken: String?
    ): Purchase

    companion object Factory {
        fun create(
            context: Context,
            acknowledgePurchases: Boolean,
            obfuscatedUserIdProvider: ObfuscatedUserIdProvider,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): Billing {
            return GoogleBilling(
                clientFactory = GoogleBilling.BillingClientFactory(context),
                obfuscatedUserIdProvider = obfuscatedUserIdProvider,
                ioDispatcher = ioDispatcher,
                acknowledgePurchases = acknowledgePurchases
            )
        }
    }
}

private const val RECONNECT_TIMER_START_MILLISECONDS = 1000L
private const val RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 15 * 60 * 1000L // 15 minutes
private const val CONNECTION_TIMEOUT_MS = 30_000L
private const val BILLING_OPERATION_TIMEOUT_MS = 15_000L
private const val MAX_RETRY_ATTEMPTS = 3
private const val INITIAL_RETRY_DELAY_MS = 1000L

@Singleton
internal class GoogleBilling @Inject constructor(
    private val acknowledgePurchases: Boolean,
    private val clientFactory: BillingClientFactory,
    private val obfuscatedUserIdProvider: ObfuscatedUserIdProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Billing, PurchasesUpdatedListener {

    private val coroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val connectionMutex = Mutex()

    @Volatile
    private var billingClient: BillingClient? = null

    private val isConnecting = AtomicBoolean(false)
    private val reconnectionAlreadyScheduled = AtomicBoolean(false)
    private val connectionAttempts = AtomicInteger(0)

    // Exponential backoff for reconnection
    @Volatile
    private var reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS

    // Thread-safe purchase processing
    private val purchasesJobs = ConcurrentHashMap<String, Job>()

    private val _billingConnectionState = MutableStateFlow<BillingConnectionState>(
        BillingConnectionState.Disconnected
    )

    override val connectionState: StateFlow<BillingConnectionState> =
        _billingConnectionState.asStateFlow()

    @Volatile
    private var billingFlowListener: BillingFlowListener? = null

    @Volatile
    private var onPurchasesUpdatedListener: OnPurchasesUpdatedListener? = null

    class BillingClientFactory(private val context: Context) {
        @UiThread
        fun buildClient(listener: PurchasesUpdatedListener): BillingClient {
            return BillingClient.newBuilder(context)
                .setListener(listener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enablePrepaidPlans()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()
        }
    }

    override suspend fun connect(): BillingConnectionState = withTimeout(CONNECTION_TIMEOUT_MS) {
        connectionMutex.withLock {
            val currentState = _billingConnectionState.value

            // Return immediately if already connected
            if (currentState is BillingConnectionState.Connected) {
                return@withTimeout currentState
            }

            // If currently connecting, wait for the result
            if (isConnecting.get()) {
                return@withTimeout connectionState.first { it !is BillingConnectionState.Disconnected }
            }

            performConnection()
        }
    }

    private suspend fun performConnection(): BillingConnectionState {
        try {
            isConnecting.set(true)

            // Create new client if needed
            if (billingClient?.isReady != true) {
                billingClient?.endConnection()
                billingClient = clientFactory.buildClient(this)
                Timber.tag(TAG).d("Created new billing client")
            }

            // Start connection
            val connectionResult = suspendCancellableCoroutine { continuation ->
                val stateListener = object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        when (billingResult.responseCode) {
                            BillingResponseCode.OK -> {
                                val connectedState = BillingConnectionState.Connected
                                _billingConnectionState.tryEmit(connectedState)
                                connectionAttempts.set(0)
                                reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS

                                coroutineScope.launch { onBillingSetupFinished() }
                                continuation.resume(connectedState)
                            }

                            else -> {
                                val error = BillingException.from(billingResult)
                                val errorState = BillingConnectionState.Error(error)
                                _billingConnectionState.tryEmit(errorState)
                                continuation.resumeWithException(error)
                            }
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        _billingConnectionState.tryEmit(BillingConnectionState.Disconnected)
                        scheduleReconnection()
                    }
                }

                try {
                    billingClient?.startConnection(stateListener)
                        ?: continuation.resumeWithException(
                            BillingException.DeveloperErrorException("BillingClient is null")
                        )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to start billing connection")
                    continuation.resumeWithException(
                        BillingException.DeveloperErrorException("Failed to start connection: ${e.message}")
                    )
                }
            }

            return connectionResult
        } finally {
            isConnecting.set(false)
        }
    }

    override fun setOnPurchasesUpdatedListener(listener: OnPurchasesUpdatedListener) {
        onPurchasesUpdatedListener = listener
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            try {
                // Cancel all pending jobs
                purchasesJobs.values.forEach { it.cancel() }
                purchasesJobs.clear()

                // Reset states
                isConnecting.set(false)
                reconnectionAlreadyScheduled.set(false)
                billingFlowListener = null

                // Disconnect client
                billingClient?.endConnection()
                billingClient = null

                _billingConnectionState.tryEmit(BillingConnectionState.Disconnected)

                Timber.tag(TAG).d("Billing client disconnected")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during disconnect")
            }
        }
    }

    private fun scheduleReconnection() {
        if (reconnectionAlreadyScheduled.compareAndSet(false, true)) {
            val attempts = connectionAttempts.incrementAndGet()

            if (attempts > MAX_RETRY_ATTEMPTS) {
                Timber.tag(TAG).w("Max reconnection attempts reached")
                return
            }

            coroutineScope.launch {
                try {
                    delay(reconnectMilliseconds)
                    reconnectionAlreadyScheduled.set(false)

                    connect()

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Reconnection failed")
                    reconnectionAlreadyScheduled.set(false)
                } finally {
                    // Exponential backoff
                    reconnectMilliseconds = min(
                        reconnectMilliseconds * 2,
                        RECONNECT_TIMER_MAX_TIME_MILLISECONDS
                    )
                }
            }
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        productId: String,
        productType: String,
        selectedOfferToken: String?,
    ): Purchase {
        Timber.tag(TAG).d("Launching billing flow for product $productId")

        ensureConnection()

        val productDetails = getProducts(listOf(productId), productType)
            .firstOrNull()
            ?: throw BillingException.DeveloperErrorException("Product $productId not found")

        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productType == ProductType.SUBS) {
            params.setOfferToken(
                selectedOfferToken
                    ?: productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    ?: throw BillingException.DeveloperErrorException("No offer token available")
            )
        }

        val obfuscatedUserId = obfuscatedUserIdProvider.provideObfuscatedUserId()

        val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params.build()))

        if (obfuscatedUserId != null) {
            billingFlowParamsBuilder
                .setObfuscatedAccountId(obfuscatedUserId.obfuscatedAccountId)
                .setObfuscatedProfileId(obfuscatedUserId.obfuscatedProfileId)
        } else {
            Timber.tag(TAG).w("Obfuscated user ID not available")
        }

        val result = withConnectedClient {
            launchBillingFlow(activity, billingFlowParamsBuilder.build())
        }

        if (result.responseCode != BillingResponseCode.OK) {
            throw BillingException.from(result)
        }

        return suspendCancellableCoroutine { continuation ->
            billingFlowListener = object : BillingFlowListener {
                override fun onSuccess(purchase: Purchase) {
                    billingFlowListener = null
                    continuation.resume(purchase)
                }

                override fun onError(e: BillingException) {
                    billingFlowListener = null
                    continuation.resumeWithException(e)
                }
            }

            continuation.invokeOnCancellation {
                billingFlowListener = null
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Timber.tag(TAG).d(
            "Purchases updated: ${billingResult.message}. " +
                    "Purchases: ${purchases?.joinToString { it.products.firstOrNull() ?: "unknown" }}"
        )

        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    coroutineScope.launch {
                        try {
                            processPurchases(purchases)
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error processing purchases")
                        }
                    }
                }

                onPurchasesUpdatedListener?.onPurchasesUpdated(
                    purchases?.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                )
            }

            else -> {
                billingFlowListener?.onError(BillingException.from(billingResult))
            }
        }
    }

    override suspend fun getProducts(
        productIds: List<String>,
        productType: String,
    ): List<ProductDetails> = withTimeout(BILLING_OPERATION_TIMEOUT_MS) {
        if (productIds.isEmpty()) return@withTimeout emptyList()

        ensureConnection()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                }
            )
            .build()

        val result = withConnectedClient { queryProductDetails(params) }

        when (result.billingResult.responseCode) {
            BillingResponseCode.OK -> {
                val products = result.productDetailsList.orEmpty()
                Timber.tag(TAG).d("Retrieved ${products.size} products for type $productType")
                return@withTimeout products
            }

            else -> throw BillingException.from(result.billingResult)
        }
    }

    override suspend fun queryAllPurchases(): List<Purchase> {
        ensureConnection()

        return coroutineScope {
            val subsDeferred = async { queryPurchases(ProductType.SUBS) }
            val inAppDeferred = async { queryPurchases(ProductType.INAPP) }

            val subsPurchases = subsDeferred.await()
            val inAppPurchases = inAppDeferred.await()

            (subsPurchases + inAppPurchases)
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .also { purchases ->
                    Timber.tag(TAG).d("Found ${purchases.size} purchased items")
                }
        }
    }

    override suspend fun queryPurchases(productType: String): List<Purchase> =
        withTimeout(BILLING_OPERATION_TIMEOUT_MS) {
            ensureConnection()

            val (billingResult, purchases) = withConnectedClient {
                withContext(ioDispatcher) {
                    queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(productType)
                            .build()
                    )
                }
            }

            if (billingResult.responseCode != BillingResponseCode.OK) {
                throw BillingException.from(billingResult)
            }

            // Process purchases asynchronously
            if (purchases.isNotEmpty()) {
                coroutineScope.launch {
                    processPurchases(purchases)
                }
            }

            purchases
        }

    override suspend fun queryPurchaseHistory(productType: String): List<PurchaseHistoryRecord> =
        withTimeout(BILLING_OPERATION_TIMEOUT_MS) {
            ensureConnection()

            withContext(ioDispatcher) {
                val params = QueryPurchaseHistoryParams.newBuilder()
                    .setProductType(productType)
                    .build()

                val result = withConnectedClient { queryPurchaseHistory(params) }

                when (result.billingResult.responseCode) {
                    BillingResponseCode.OK -> {
                        result.purchaseHistoryRecordList.orEmpty().also { history ->
                            Timber.tag(TAG)
                                .d("Retrieved ${history.size} history records for $productType")
                        }
                    }

                    else -> throw BillingException.from(result.billingResult)
                }
            }
        }

    override suspend fun getBillingConfig(): BillingConfig? =
        withTimeout(BILLING_OPERATION_TIMEOUT_MS) {
            ensureConnection()

            val params = GetBillingConfigParams.newBuilder().build()

            withConnectedClient {
                suspendCoroutine { continuation ->
                    getBillingConfigAsync(params) { billingResult, billingConfig ->
                        when (billingResult.responseCode) {
                            BillingResponseCode.OK -> {
                                Timber.tag(TAG)
                                    .d("Billing config retrieved: ${billingConfig?.countryCode}")
                                continuation.resume(billingConfig)
                            }

                            else -> {
                                continuation.resumeWithException(
                                    BillingException.from(
                                        billingResult
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

    override suspend fun showInAppMessages(activity: Activity) {
        ensureConnection()
        withConnectedClient {
            val inAppMessageParams = InAppMessageParams.newBuilder()
                .addInAppMessageCategoryToShow(InAppMessageCategoryId.TRANSACTIONAL)
                .build()

            val billingResult =
                showInAppMessages(activity, inAppMessageParams) { inAppMessageResult ->
                    when (inAppMessageResult.responseCode) {
                        InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED -> {
                            val purchaseToken = inAppMessageResult.purchaseToken
                            if (purchaseToken != null) {
                                coroutineScope.launch {
                                    queryPurchases(ProductType.SUBS)
                                }
                            }
                        }
                    }
                }

            if (billingResult.responseCode != BillingResponseCode.OK) {
                throw BillingException.from(billingResult)
            }
        }
    }

    private suspend fun ensureConnection() {
        val currentState = connectionState.value
        when (currentState) {
            is BillingConnectionState.Connected -> return
            is BillingConnectionState.Error -> throw currentState.e
            BillingConnectionState.Disconnected -> {
                connect() // This will throw if connection fails
            }
        }
    }

    private inline fun <T> withConnectedClient(action: BillingClient.() -> T): T {
        val client = billingClient
            ?: throw BillingException.DeveloperErrorException("BillingClient is null")

        if (!client.isReady) {
            throw BillingException.ServiceDisconnectedException("BillingClient is not ready")
        }

        return client.action()
    }

    private suspend fun onBillingSetupFinished() {
        try {
            val purchases = queryAllPurchases()
            val history = queryPurchaseHistory(ProductType.INAPP)

            Timber.tag(TAG)
                .d("Setup complete: ${purchases.size} purchases, ${history.size} history items")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in setup completion")
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            val existingJob = purchasesJobs[purchase.purchaseToken]
            if (existingJob?.isActive != true) {
                purchasesJobs[purchase.purchaseToken] = coroutineScope.launch {
                    try {
                        processPurchase(purchase)
                    } catch (e: Exception) {
                        Timber.tag(TAG)
                            .e(e, "Error processing purchase ${purchase.purchaseToken}")
                    } finally {
                        purchasesJobs.remove(purchase.purchaseToken)
                    }
                }
            }
        }
    }

    private suspend fun processPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: "unknown"

        Timber.tag(TAG).d("Processing purchase: $productId, state: ${purchase.purchaseState}")

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                Timber.tag(TAG).d("Purchase $productId is pending")
                return
            }

            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                Timber.tag(TAG).w("Purchase $productId has unspecified state")
                return
            }

            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.isAcknowledged) {
                    Timber.tag(TAG).d("Purchase $productId already acknowledged")
                } else if (acknowledgePurchases) {
                    acknowledgePurchase(purchase.purchaseToken)
                }

                billingFlowListener?.onSuccess(purchase)
            }
        }
    }

    private suspend fun acknowledgePurchase(purchaseToken: String) {
        var currentDelay = INITIAL_RETRY_DELAY_MS
        val maxRetries = MAX_RETRY_ATTEMPTS

        repeat(maxRetries) { attempt ->
            try {
                val result = withContext(ioDispatcher) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build()

                    withConnectedClient { acknowledgePurchase(params) }
                }

                when (result.responseCode) {
                    BillingResponseCode.OK -> {
                        Timber.tag(TAG).d("Purchase $purchaseToken acknowledged successfully")
                        return
                    }

                    BillingResponseCode.ITEM_NOT_OWNED -> {
                        Timber.tag(TAG).w("Acknowledgment failed: item not owned")
                        // Refresh purchases and try again
                        val purchases = queryAllPurchases()
                        purchases.find { it.purchaseToken == purchaseToken }
                            ?.let { freshPurchase ->
                                if (!freshPurchase.isAcknowledged) {
                                    delay(currentDelay)
                                    currentDelay = min(currentDelay * 2, 30_000L)
                                    return@repeat
                                }
                            }
                        return // Item not found or already acknowledged
                    }

                    in RETRYABLE_ERRORS -> {
                        if (attempt < maxRetries - 1) {
                            Timber.tag(TAG)
                                .w("Acknowledgment failed (attempt ${attempt + 1}), retrying...")
                            delay(currentDelay)
                            currentDelay = min(currentDelay * 2, 30_000L)
                        } else {
                            throw BillingException.from(result)
                        }
                    }

                    else -> {
                        throw BillingException.from(result)
                    }
                }
            } catch (e: BillingException) {
                if (attempt == maxRetries - 1) throw e
                delay(currentDelay)
                currentDelay = min(currentDelay * 2, 30_000L)
            }
        }
    }

    private companion object {
        private const val TAG = "Billing"

        private val RETRYABLE_ERRORS = setOf(
            BillingResponseCode.ERROR,
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.NETWORK_ERROR
        )
    }
}