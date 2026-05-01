package app.core.services.attribution

import android.content.Context
import android.os.RemoteException
import app.core.services.analytics.Analytics
import app.core.services.analytics.AnalyticsEvents
import app.core.services.analytics.AnalyticsEvents
import app.core.services.core.model.Attribution
import app.core.services.data.PreferencesDataStore
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

internal class GooglePlayInstallReferrerAttributionProvider(
    private val applicationContext: Context,
    private val analytics: Analytics,
    private val preferencesDataStore: PreferencesDataStore,
    private val attributionParser: AttributionParser<String?> = GooglePlayInstallReferrerAttributionParser(),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AttributionProvider {
    override suspend fun provide(): Attribution {
        return with(coroutineDispatcher) {
            var installReferrer: String? = preferencesDataStore.getInstallReferrer()

            Timber.d("Preferences Google Play Install Referrer: $installReferrer")

            if (installReferrer == null) {
                val referrerDetails = getReferrerDetails(applicationContext)
                installReferrer = referrerDetails?.installReferrer

                if (installReferrer != null) {
                    preferencesDataStore.setInstallReferrer(installReferrer)
                }
            }

            val attribution = attributionParser.parse(installReferrer)

            val eventProperties = mutableMapOf<String, Any?>("referrer" to installReferrer)
            if (!attribution.rawData.isNullOrEmpty()) {
                eventProperties.putAll(attribution.rawData)
            }

            analytics.logEvent(
                event = AnalyticsEvents.INSTALL_REFERER,
                properties = eventProperties
            )

            Timber.d("Google Play Install Referrer attribution data: $attribution")

            attribution
        }
    }

    private suspend fun getReferrerDetails(context: Context): ReferrerDetails? {
        val deferredReferrerDetails = CompletableDeferred<ReferrerDetails?>()
        val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseInt: Int) {
                if (responseInt == InstallReferrerClient.InstallReferrerResponse.OK) {
                    deferredReferrerDetails.complete(
                        try {
                            client.installReferrer
                        } catch (e: RemoteException) {
                            Timber.e(e)
                            null
                        }
                    )
                } else {
                    deferredReferrerDetails.complete(null)
                }
                client.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (!deferredReferrerDetails.isCompleted) {
                    deferredReferrerDetails.complete(null)
                }
            }
        })

        return deferredReferrerDetails.await()
    }
}