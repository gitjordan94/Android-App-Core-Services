package app.core.services.billing.google

import com.android.billingclient.api.Purchase

fun interface OnPurchasesUpdatedListener {
    fun onPurchasesUpdated(purchases: List<Purchase>?)
}