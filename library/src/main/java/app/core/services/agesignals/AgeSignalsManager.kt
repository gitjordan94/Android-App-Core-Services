package app.core.services.agesignals

import android.content.Context
import app.core.services.analytics.Analytics
import app.core.services.analytics.NoOpAnalytics

interface AgeSignalsManager {
    fun requestAgeSignals(listener: AgeSignalsListener)

    class Builder(private val context: Context) {
        private var analytics: Analytics = NoOpAnalytics

        fun analytics(analytics: Analytics) = apply {
            this.analytics = analytics
        }

        fun build(): AgeSignalsManager {
            return GooglePlayAgeSignalsManager(
                applicationContext = context.applicationContext,
                analytics = analytics
            )
        }
    }
}