package app.core.services.billing.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.core.services.billing.model.ProductType
import app.core.services.billing.model.PurchaseState

@Entity(tableName = PurchaseEntity.TABLE_NAME)
internal data class PurchaseEntity(
    @PrimaryKey
    @ColumnInfo(name = COLUMN_PURCHASE_TOKEN)
    val purchaseToken: String,

    @ColumnInfo(name = COLUMN_ORDER_ID)
    val orderId: String?,

    @ColumnInfo(name = COLUMN_ACKNOWLEDGED)
    val isAcknowledged: Boolean,

    @ColumnInfo(name = COLUMN_PRODUCT_TYPE)
    val productType: ProductType,

    @ColumnInfo(name = COLUMN_PURCHASE_STATE)
    val purchaseState: PurchaseState,

    @ColumnInfo(name = COLUMN_PURCHASE_TIME)
    val purchaseTime: Long,

    @ColumnInfo(name = COLUMN_USER_ID)
    val userId: String?,
) {
    internal companion object {
        internal const val TABLE_NAME = "purchases"
        internal const val COLUMN_PURCHASE_TOKEN = "purchase_token"
        internal const val COLUMN_PRODUCT_TYPE = "product_type"
        internal const val COLUMN_ORDER_ID = "order_id"
        internal const val COLUMN_ACKNOWLEDGED = "acknowledged"
        internal const val COLUMN_PURCHASE_STATE = "purchase_state"
        internal const val COLUMN_PURCHASE_TIME = "purchase_time"
        internal const val COLUMN_USER_ID = "user_id"
    }
}