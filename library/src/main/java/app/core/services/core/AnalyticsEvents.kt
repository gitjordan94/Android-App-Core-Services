package app.core.services.core

internal object AnalyticsEvents {
    const val APP_LAUNCH = "app_launch"
    const val FIRST_LAUNCH = "first_launch"
    const val TEST_DISTRIBUTION = "test_distribution"
    const val PURCHASE = "purchase"
    const val TRIAL_STARTED = "trial_started"
    const val PURCHASE_ERROR = "purchase_error"
}

internal object AnalyticsProperties {
    const val COHORT_DAY = "cohort_day"
    const val COHORT_WEEK = "cohort_week"
    const val COHORT_MONTH = "cohort_month"
    const val COHORT_YEAR = "cohort_year"

    const val ACTIVE_SUBS = "active_subscriptions"
    const val ALL_PURCHASED_PRODUCT_IDS = "all_purchased_product_ids"
}