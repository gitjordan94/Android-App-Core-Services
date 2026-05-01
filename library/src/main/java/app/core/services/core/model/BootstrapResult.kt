package app.core.services.core.model

import app.core.services.billing.model.Purchases

data class BootstrapResult(
    val activePaywall: String,
    val attribution: Attribution,
    val storeCountry: String? = null,
    val purchases: Purchases? = null,
    val isFirstLaunch: Boolean
)