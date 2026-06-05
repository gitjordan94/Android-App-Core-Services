package app.core.services.billing.logger

import app.core.services.billing.model.Purchase

internal interface PurchaseEventLogger {
    fun logPurchase(purchase: Purchase)
}