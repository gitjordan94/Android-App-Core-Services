package app.core.services.billing.model

import androidx.annotation.Keep

@Keep
enum class ProductType {
    SUBSCRIPTION,
    ONE_TIME_PURCHASE;
}