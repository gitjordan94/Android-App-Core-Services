package app.core.services.analytics

internal object AnalyticsEvents {
    internal const val FIRST_LAUNCH = "first_launch"
    internal const val INSTALL_REFERER = "install_referrer"
    internal const val TRIAL_STARTED = "trial_started"
    internal const val AF_CONVERSION_DATA_SUCCESS = "af_conversion_data_success"
    internal const val AF_CONVERSION_DATA_FAIL = "af_conversion_data_fail"
    internal const val ATTRIBUTION = "framework_attribution"
    internal const val ATTRIBUTION_STARTED = "framework_attribution_started"
    internal const val ATTRIBUTION_FINISHED = "framework_attribution_finished"
}