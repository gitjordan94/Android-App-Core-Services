package app.core.services.billing.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.core.services.billing.db.entity.PurchaseEntity
import app.core.services.billing.db.entity.PurchaseProductEntity

@Database(
    version = 2,
    entities = [
        PurchaseEntity::class,
        PurchaseProductEntity::class
    ],
    exportSchema = false
)
internal abstract class BillingDatabase : RoomDatabase() {
    abstract val purchasesDao: PurchasesDao

    internal companion object {
        internal fun create(context: Context): BillingDatabase {
            return Room.databaseBuilder<BillingDatabase>(context, "billing_database")
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}