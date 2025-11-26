package app.core.services.billing.google

import app.core.services.billing.google.error.BillingException

internal sealed class BillingConnectionState {
    data object Connected : BillingConnectionState()

    data class Error(val e: BillingException) : BillingConnectionState()

    data object Disconnected : BillingConnectionState()
}