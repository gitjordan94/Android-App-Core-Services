package app.core.services.billing.google.extensions

import app.core.services.billing.google.error.BillingException
import app.core.services.billing.google.error.BillingException.ServiceDisconnectedException
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun BillingClient.connectOrThrow(): BillingClient {
    return suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)

        val billingClientStateListener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (!finished.compareAndSet(false, true)) {
                    return
                }

                if (result.responseCode == BillingResponseCode.OK) {
                    continuation.resume(this@connectOrThrow)
                } else {
                    val ex = BillingException.from(result)

                    continuation.resumeWithException(ex)

                    try {
                        endConnection()
                    } catch (e: Throwable) {
                        Timber.e(e, "Failed to end connection")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                if (!finished.compareAndSet(false, true)) {
                    return
                }

                val ex = ServiceDisconnectedException("Billing service disconnected during setup")
                continuation.resumeWithException(ex)

                try {
                    endConnection()
                } catch (e: Throwable) {
                    Timber.e(e, "Failed to end connection")
                }
            }
        }

        continuation.invokeOnCancellation {
            if (!finished.compareAndSet(false, true)) {
                return@invokeOnCancellation
            }

            Timber.d(it, "Connection await was cancelled")

            try {
                endConnection()
            } catch (e: Throwable) {
                Timber.e(e, "Failed to end connection on cancellation")
            }
        }

        try {
            startConnection(billingClientStateListener)
        } catch (e: Throwable) {
            Timber.e(e, "Failed to start connection")

            if (finished.compareAndSet(false, true)) {
                continuation.resumeWithException(e)
            }
        }
    }
}