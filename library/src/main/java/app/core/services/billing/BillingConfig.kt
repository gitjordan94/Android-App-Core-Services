package app.core.services.billing

data class BillingConfig(
    val secretKey: String,
    val iv: String,
    val consumedInAppPurchasesTimeMillis: Long? = null,
    val acknowledgePurchases: Boolean = true,
)