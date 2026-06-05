package app.core.services.billing.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.core.services.billing.db.entity.PurchaseEntity
import app.core.services.billing.db.entity.PurchaseProductEntity
import app.core.services.billing.db.entity.PurchaseWithProducts
import app.core.services.billing.db.entity.toEntity
import app.core.services.billing.model.PurchaseDetails
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class PurchasesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(purchase: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: PurchaseProductEntity)

    @Transaction
    @Query("SELECT * FROM ${PurchaseEntity.TABLE_NAME}")
    abstract fun getAllPurchasesFlow(): Flow<List<PurchaseWithProducts>>

    @Transaction
    @Query("SELECT * FROM ${PurchaseEntity.TABLE_NAME}")
    abstract suspend fun getAllPurchases(): List<PurchaseWithProducts>

    @Query("DELETE FROM ${PurchaseProductEntity.TABLE_NAME}")
    abstract suspend fun deleteAllProducts()

    @Query("DELETE FROM ${PurchaseEntity.TABLE_NAME}")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun upsertAll(purchases: List<PurchaseDetails>) {
        deleteAllProducts()
        deleteAll()
        for (purchase in purchases) {
            insert(purchase.toEntity())
            for (productId in purchase.productIds) {
                insert(PurchaseProductEntity(productId, purchase.purchaseToken))
            }
        }
    }
}