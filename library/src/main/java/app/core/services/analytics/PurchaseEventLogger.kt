package app.core.services.analytics

import app.core.services.billing.model.Purchase

interface PurchaseEventLogger {
    fun logPurchase(purchase: Purchase)
}