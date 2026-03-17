package app.core.services.agesignals

import android.content.Context
import app.core.services.agesignals.model.AgeSignalsAnalyticsEvent
import app.core.services.agesignals.model.AgeSignalsErrorCode
import app.core.services.agesignals.model.AgeSignalsException
import app.core.services.agesignals.util.toInternal
import app.core.services.analytics.Analytics
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import timber.log.Timber
import com.google.android.play.agesignals.AgeSignalsException as ExternalAgeSignalsException

internal class GooglePlayAgeSignalsManager(
    applicationContext: Context,
    private val analytics: Analytics
) : AgeSignalsManager {
    private val ageSignalsManager = AgeSignalsManagerFactory.create(applicationContext)

    override fun requestAgeSignals(listener: AgeSignalsListener) {
        ageSignalsManager.checkAgeSignals(AgeSignalsRequest.builder().build())
            .addOnSuccessListener {
                val result = it.toInternal()

                Timber.d("Age Signals result: $result")

                analytics.logEvent(AgeSignalsAnalyticsEvent.Success)

                listener.onSuccess(result)
            }
            .addOnFailureListener { e ->
                val error = if (e is ExternalAgeSignalsException) {
                    e.toInternal()
                } else {
                    AgeSignalsException(e.message, AgeSignalsErrorCode.UNKNOWN)
                }

                Timber.w(e, "Age Signals error: $error")

                analytics.logEvent(AgeSignalsAnalyticsEvent.Error(error))

                listener.onError(error)
            }
    }
}