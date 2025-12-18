package app.core.services.appsflyer

import android.content.Context
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvent
import app.core.services.appsflyer.error.AppsFlyerAttributionFailureException
import app.core.services.appsflyer.error.AppsFlyerConversionFailureException
import app.core.services.billing.logger.PurchaseEventLogger
import app.core.services.billing.model.Purchase
import com.appsflyer.AFInAppEventType
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

class AppsFlyerAnalytics(
    devKey: String,
    private val applicationContext: Context
) : Analytics, PurchaseEventLogger {
    private val appsFlyer = AppsFlyerLib.getInstance()

    private val conversionDataFlow = MutableStateFlow<Map<String, Any?>?>(null)

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
                    conversionDataFlow.value = conversionData
                    appsFlyer.unregisterConversionListener()
                }

                override fun onConversionDataFail(errorMessage: String?) {
                    Timber.e(
                        AppsFlyerConversionFailureException(errorMessage ?: "Unknown error"),
                        "AppsFlyer conversion data fail: $errorMessage."
                    )
                    conversionDataFlow.value = emptyMap()
                    appsFlyer.unregisterConversionListener()
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

    override fun logPurchase(purchase: Purchase) {
        if (purchase.price.amountMicros == 0L) {
            logEvent(AFInAppEventType.START_TRIAL)
        } else {
            logEvent(AFInAppEventType.PURCHASE, mapOf("productId" to purchase.product.id))
        }
    }

    internal fun setAdditionalData(data: Map<String, Any>) {
        appsFlyer.setAdditionalData(data)
    }

    internal suspend fun awaitConversionData(): Map<String, Any?>? {
        return conversionDataFlow
            .filterNotNull()
            .firstOrNull()
    }
}