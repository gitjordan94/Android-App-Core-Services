package app.core.services.appsflyer

import android.content.Context
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvent
import app.core.services.appsflyer.error.AppsFlyerAttributionFailureException
import app.core.services.appsflyer.error.AppsFlyerConversionFailureException
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

internal class AppsFlyerAnalytics(
    devKey: String,
    private val applicationContext: Context,
    private val appsFlyer: AppsFlyerLib = AppsFlyerLib.getInstance()
) : Analytics {
    private val _conversionDataFlow =
        MutableStateFlow<ConversionDataResult>(ConversionDataResult.Loading)
    internal val conversionDataFlow = _conversionDataFlow.asStateFlow()

    internal var appsFlyerUID: String? = null
        private set
        get() {
            if (field == null) {
                field = appsFlyer.getAppsFlyerUID(applicationContext)
            }

            return field
        }

    init {
        appsFlyer.init(
            devKey,
            object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(conversionData: MutableMap<String, Any>?) {
                    Timber.d("onConversionDataSuccess: $conversionData.")
                    _conversionDataFlow.value = ConversionDataResult.Success(conversionData)
                }

                override fun onConversionDataFail(errorMessage: String?) {
                    Timber.e(
                        AppsFlyerConversionFailureException(errorMessage ?: "Unknown error"),
                        "AppsFlyer conversion data fail: $errorMessage."
                    )

                    _conversionDataFlow.value = ConversionDataResult.Error(errorMessage)
                }

                override fun onAppOpenAttribution(attributionData: MutableMap<String, String>?) {
                    Timber.d("onAppOpenAttribution: $attributionData.")
                }

                override fun onAttributionFailure(errorMessage: String?) {
                    Timber.e(
                        AppsFlyerAttributionFailureException(errorMessage ?: "Unknown error"),
                        "AppsFlyer attribution failure: $errorMessage."
                    )
                }
            },
            applicationContext
        )

        start()
    }

    internal fun start() {
        appsFlyer.start(applicationContext)
    }

    override fun setUserProperties(properties: Map<String, Any?>?) {
        // do nothing
    }

    override fun logEvent(event: String, properties: Map<String, Any?>?) {
        appsFlyer.logEvent(applicationContext, event, properties)
    }

    override fun logEvent(event: AnalyticsEvent) {
        logEvent(event.type, event.properties)
    }

    internal fun setAdditionalData(data: Map<String, Any>) {
        appsFlyer.setAdditionalData(data)
    }
}