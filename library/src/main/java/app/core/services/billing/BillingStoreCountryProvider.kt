package app.core.services.billing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface BillingStoreCountryProvider {
    suspend fun getStoreCountry(): String?
}

internal class GoogleBillingStoreCountryProvider(
    private val billingClient: BillingClient,
) : BillingStoreCountryProvider {
    @Volatile
    private var storeCountry: String? = null

    private val mutex = Mutex()

    override suspend fun getStoreCountry(): String? {
        if (storeCountry != null) {
            return storeCountry
        }

        return mutex.withLock {
            if (storeCountry == null) {
                storeCountry = billingClient.getStoreCountry()
            }

            storeCountry
        }
    }
}