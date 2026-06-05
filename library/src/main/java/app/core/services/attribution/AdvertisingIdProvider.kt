package app.core.services.attribution

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.core.services.attribution.model.AdvertisingId
import timber.log.Timber

internal interface AdvertisingIdProvider {
    suspend fun provide(): AdvertisingId

    companion object {
        fun create(applicationContext: Context): AdvertisingIdProvider {
            return AdvertisingIdProviderImpl(applicationContext)
        }
    }
}

internal class AdvertisingIdProviderImpl(
    private val applicationContext: Context,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AdvertisingIdProvider {
    override suspend fun provide(): AdvertisingId {
        return withContext(coroutineDispatcher) {
            try {
                val info = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext)

                if (info.id == "00000000-0000-0000-0000-000000000000") {
                    AdvertisingId(id = null, isLimitAdTrackingEnabled = true)
                }

                AdvertisingId(info.id, info.isLimitAdTrackingEnabled)
            } catch (e: Throwable) {
                Timber.e(e)
                AdvertisingId(id = null, isLimitAdTrackingEnabled = true)
            }
        }
    }
}