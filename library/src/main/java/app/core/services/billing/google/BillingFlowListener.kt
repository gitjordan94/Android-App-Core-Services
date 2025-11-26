package app.core.services.billing.google

import com.android.billingclient.api.Purchase
import app.core.services.billing.google.error.BillingException

internal interface BillingFlowListener {
    fun onSuccess(purchase: Purchase)

    fun onError(e: BillingException)
}