package app.core.services.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.core.services.billing.model.Purchases
import timber.log.Timber

internal interface PurchasesDataStore {
    suspend fun setActiveSubscriptions(productIds: Set<String>)

    suspend fun setAllPurchasedProductIds(productIds: Set<String>)

    fun getPurchases(): Flow<Purchases>

    suspend fun setPurchases(purchases: Purchases?)
}

internal class PurchasesPreferencesDataStore(
    private val preferencesDataStore: DataStore<Preferences>
) : PurchasesDataStore {
    private val keyActiveSubscriptions = stringSetPreferencesKey("active_subscriptions")
    private val keyAllPurchasedProductIds = stringSetPreferencesKey("all_purchased_product_ids")

    override suspend fun setActiveSubscriptions(productIds: Set<String>) {
        preferencesDataStore.edit {
            it[keyActiveSubscriptions] = productIds
        }
    }

    override suspend fun setAllPurchasedProductIds(productIds: Set<String>) {
        preferencesDataStore.edit {
            it[keyAllPurchasedProductIds] = productIds
        }
    }

    override fun getPurchases(): Flow<Purchases> {
        return preferencesDataStore.data.map {
            Purchases(
                activeSubscriptions = it[keyActiveSubscriptions].orEmpty(),
                allPurchasedProductIds = it[keyAllPurchasedProductIds].orEmpty()
            )
        }
    }

    override suspend fun setPurchases(purchases: Purchases?) {
        if (purchases == null) {
            return
        }

        try {
            preferencesDataStore.edit {
                it[keyActiveSubscriptions] = purchases.activeSubscriptions
                it[keyAllPurchasedProductIds] = purchases.allPurchasedProductIds
            }
        } catch (e: Throwable) {
            Timber.e(e)
        }
    }

    companion object {
        fun create(applicationContext: Context): PurchasesDataStore {
            val dataStore = PreferenceDataStoreFactory.create {
                applicationContext.preferencesDataStoreFile("app_core_services_purchases")
            }

            return PurchasesPreferencesDataStore(dataStore)
        }
    }
}