package app.core.services.billing.model

import androidx.annotation.Keep

@Keep
enum class PurchaseState {
    PURCHASED,
    PENDING,
    UNKNOWN
}