package app.core.services.core.appsetid

import android.content.Context
import com.google.android.gms.appset.AppSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class AndroidAppSetIdProvider(
    private val applicationContext: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AppSetIdProvider {
    override suspend fun provide(): String? {
        return withContext(dispatcher) {
            try {
                val appSetIdInfo = AppSet.getClient(applicationContext)
                    .appSetIdInfo
                    .await()

                appSetIdInfo.id
            } catch (e: Throwable) {
                Timber.e(e, "Failed to retrieve AppSetId")
                null
            }
        }
    }
}