package app.core.services.agesignals.util

import app.core.services.agesignals.AgeSignalsListener
import app.core.services.agesignals.AgeSignalsManager
import app.core.services.agesignals.model.AgeSignalsException
import app.core.services.agesignals.model.AgeSignalsResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Throws(AgeSignalsException::class)
suspend fun AgeSignalsManager.requestAgeSignals(): AgeSignalsResult {
    return suspendCancellableCoroutine { continuation ->
        requestAgeSignals(object : AgeSignalsListener {
            override fun onSuccess(result: AgeSignalsResult) {
                continuation.resume(result)
            }

            override fun onError(e: AgeSignalsException) {
                continuation.resumeWithException(e)
            }
        })
    }
}