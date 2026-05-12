package app.core.services.attribution.installreferrer

import android.content.Context
import android.os.RemoteException
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

private const val TIMEOUT_MS = 10_000L

internal class GoogleInstallReferrerProvider(
    private val applicationContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InstallReferrerProvider {
    override suspend fun getInstallReferrer(): InstallReferrerDetails? {
        return withContext(ioDispatcher) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val client = InstallReferrerClient.newBuilder(applicationContext)
                        .build()

                    continuation.invokeOnCancellation { client.endConnection() }
                    client.startConnection(object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseInt: Int) {
                            val result =
                                if (responseInt == InstallReferrerClient.InstallReferrerResponse.OK) {
                                    try {
                                        client.installReferrer?.let {
                                            InstallReferrerDetails(
                                                installReferrer = it.installReferrer,
                                                referrerClickTimestampSeconds = it.referrerClickTimestampSeconds,
                                                installBeginTimestampSeconds = it.installBeginTimestampSeconds,
                                                googlePlayInstant = it.googlePlayInstantParam,
                                                referrerClickTimestampServerSeconds = it.referrerClickTimestampServerSeconds,
                                                installBeginTimestampServerSeconds = it.installBeginTimestampServerSeconds,
                                                installVersion = it.installVersion,
                                            )
                                        }
                                    } catch (e: RemoteException) {
                                        Timber.e(e)
                                        null
                                    }
                                } else null

                            client.endConnection()
                            continuation.resume(result)
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            }
        }
    }
}