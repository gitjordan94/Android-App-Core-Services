package app.core.services.billing

data class BillingConfig(
    val secretKey: String,
    val iv: String,
    val acknowledgePurchases: Boolean = true,
)