package app.core.services.core.model

import app.core.services.billing.model.Purchases

data class ConfigurationResult(
    val attribution: Attribution,
    val purchases: Purchases? = null,
    val isFirstLaunch: Boolean
)