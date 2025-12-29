package app.core.services.analytics.amplitude

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.sessionreplay.config.MaskLevel
import com.amplitude.android.sessionreplay.config.PrivacyConfig
import com.amplitude.common.Logger
import com.amplitude.core.events.Identify
import app.core.services.BuildConfig
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvent
import app.core.services.analytics.amplitude.sessionreplay.SessionReplayConfig
import app.core.services.core.AnalyticsEvents
import app.core.services.core.AnalyticsProperties
import app.core.services.billing.logger.PurchaseEventLogger
import app.core.services.billing.model.Purchase
import timber.log.Timber
import java.util.Calendar

internal class AmplitudeAnalytics(
    context: Context,
    apiKey: String,
    sessionReplayConfig: SessionReplayConfig,
) : Analytics, PurchaseEventLogger {
    private val amplitude = Amplitude(apiKey, context) {
        flushIntervalMillis = 10_000
        flushEventsOnClose = true
    }

    private val sessionReplayPlugin = SessionReplayPlugin(
        sampleRate = sessionReplayConfig.sampleRate,
        enableRemoteConfig = sessionReplayConfig.enableRemoteConfig,
        privacyConfig = PrivacyConfig(
            maskLevel = when (sessionReplayConfig.maskLevel) {
                SessionReplayConfig.MaskLevel.LIGHT -> MaskLevel.LIGHT
                SessionReplayConfig.MaskLevel.MEDIUM -> MaskLevel.MEDIUM
                SessionReplayConfig.MaskLevel.CONSERVATIVE -> MaskLevel.CONSERVATIVE
            }
        )
    )

    init {
        amplitude.logger.logMode = if (BuildConfig.DEBUG) {
            Logger.LogMode.DEBUG
        } else {
            Logger.LogMode.INFO
        }

        if (sessionReplayConfig.autoStart) {
            enableSessionReplay()
        }
    }

    internal fun setUserId(userId: String?) {
        amplitude.setUserId(userId)
    }

    internal fun reset() {
        amplitude.reset()
    }

    internal fun getUserId(): String? {
        return amplitude.getUserId()
    }

    override fun logEvent(event: String, properties: Map<String, Any?>?) {
        amplitude.track(event, properties)
        Timber.d("Log event[$event] with properties[$properties].")
    }

    override fun logEvent(event: AnalyticsEvent) {
        logEvent(event.type, event.properties)
    }

    override fun logPurchase(purchase: Purchase) {
        if (purchase.price.amountMicros == 0L) {
            logEvent(AnalyticsEvents.TRIAL_STARTED)
        } else {
            logEvent(
                event = AnalyticsEvents.PURCHASE, properties = mapOf(
                    "productId" to purchase.product.id,
                    "price" to purchase.price.amount,
                    "currency" to purchase.price.currencyCode,
                )
            )
        }
    }

    internal fun flush() {
        amplitude.flush()
    }

    override fun setUserProperties(properties: Map<String, Any?>?) {
        amplitude.identify(properties)
        Timber.d("Set user properties[$properties].")
    }

    @Suppress("UNCHECKED_CAST")
    override fun setUserPropertiesOnce(properties: Map<String, Any?>?) {
        val identify = Identify()

        properties?.forEach { (key, value) ->
            when (value) {
                is String -> identify.setOnce(key, value)
                is Long -> identify.setOnce(key, value)
                is Int -> identify.setOnce(key, value)
                is Boolean -> identify.setOnce(key, value)
                is Float -> identify.setOnce(key, value)
                is Double -> identify.setOnce(key, value)

                // Arrays
                is Array<*> -> when {
                    value.isArrayOf<String>() -> identify.setOnce(key, value as Array<String>)
                    value.isArrayOf<Boolean>() -> identify.setOnce(key, value as Array<Boolean>)
                    value.isArrayOf<Int>() -> identify.setOnce(key, value as Array<Int>)
                    value.isArrayOf<Long>() -> identify.setOnce(key, value as Array<Long>)
                    value.isArrayOf<Float>() -> identify.setOnce(key, value as Array<Float>)
                    value.isArrayOf<Double>() -> identify.setOnce(key, value as Array<Double>)
                    else -> {
                        Timber.d("User property[$key] is not supported")
                    }
                }

                // Lists
                is List<*> -> identify.setOnce(key, value as List<Any>)
                // Maps
                is Map<*, *> -> identify.setOnce(key, value as Map<String, Any>)
                null -> {
                    Timber.d("User property[$key] is null")
                }

                else -> {
                    Timber.d("User property[$key] is not supported")
                }
            }

            Timber.d("Set user properties once[$properties].")
        }

        amplitude.identify(identify)
    }

    override fun enableSessionReplay() {
        Timber.d("Enable session replay.")
        amplitude.add(sessionReplayPlugin)
    }

    override fun disableSessionReplay() {
        Timber.d("Disable session replay.")
        amplitude.remove(sessionReplayPlugin)
    }

    internal fun sendCohort() {
        val calendar = Calendar.getInstance()

        val identify = Identify()
            .setOnce(AnalyticsProperties.COHORT_DAY, calendar[Calendar.DAY_OF_YEAR])
            .setOnce(AnalyticsProperties.COHORT_MONTH, calendar[Calendar.MONTH] + 1)
            .setOnce(AnalyticsProperties.COHORT_WEEK, calendar[Calendar.WEEK_OF_YEAR])
            .setOnce(AnalyticsProperties.COHORT_YEAR, calendar[Calendar.YEAR])

        amplitude.identify(identify)
        Timber.d("Set cohort properties.")
    }
}