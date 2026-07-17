package app.core.services.billing.db.entity

import androidx.room.Embedded
import androidx.room.Relation
import app.core.services.billing.model.PurchaseDetails

internal data class PurchaseWithProducts(
    @Embedded val purchase: PurchaseEntity,
    @Relation(
        parentColumn = PurchaseEntity.COLUMN_PURCHASE_TOKEN,
        entityColumn = PurchaseProductEntity.COLUMN_PURCHASE_TOKEN
    )
    val products: List<PurchaseProductEntity>
)

internal fun PurchaseWithProducts.toPurchaseData(): PurchaseDetails = PurchaseDetails(
    productIds = products.map { it.productId },
    orderId = purchase.orderId,
    purchaseToken = purchase.purchaseToken,
    productType = purchase.productType,
    isAcknowledged = purchase.isAcknowledged,
    purchaseTime = purchase.purchaseTime,
    purchaseState = purchase.purchaseState,
    userId = purchase.userId,
)

internal fun PurchaseDetails.toEntity(): PurchaseEntity = PurchaseEntity(
    purchaseToken = purchaseToken,
    orderId = orderId,
    isAcknowledged = isAcknowledged,
    productType = productType,
    purchaseState = purchaseState,
    purchaseTime = purchaseTime,
    userId = userId,
)