package app.core.services.billing.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = PurchaseProductEntity.TABLE_NAME,
    primaryKeys = [
        PurchaseProductEntity.COLUMN_PRODUCT_ID,
        PurchaseProductEntity.COLUMN_PURCHASE_TOKEN
    ],
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = [PurchaseEntity.COLUMN_PURCHASE_TOKEN],
            childColumns = [PurchaseProductEntity.COLUMN_PURCHASE_TOKEN],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [PurchaseProductEntity.COLUMN_PURCHASE_TOKEN])]
)
internal data class PurchaseProductEntity(
    @ColumnInfo(name = COLUMN_PRODUCT_ID)
    val productId: String,

    @ColumnInfo(name = COLUMN_PURCHASE_TOKEN)
    val purchaseToken: String,
) {
    internal companion object {
        internal const val TABLE_NAME = "purchase_products"
        internal const val COLUMN_PRODUCT_ID = "product_id"
        internal const val COLUMN_PURCHASE_TOKEN = "purchase_token"
    }
}