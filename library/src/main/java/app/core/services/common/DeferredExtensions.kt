package app.core.services.common

import android.os.SystemClock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Deferred<T>.getCompletedOrNull(): T? {
    if (!isCompleted || isCancelled) {
        return null
    }

    return runCatching { getCompleted() }.getOrNull()
}

internal suspend fun <T> Deferred<T>.awaitUntil(deadline: Long): T? {
    val remaining = deadline - SystemClock.elapsedRealtime()
    if (remaining <= 0) {
        return getCompletedOrNull()
    }
    return try {
        withTimeoutOrNull(remaining.milliseconds) { await() } ?: getCompletedOrNull()
    } catch (e: CancellationException) {
        Timber.e(e, "awaitUntil cancelled")
        throw e
    } catch (e: Throwable) {
        Timber.e(e, "awaitUntil swallowed exception")
        getCompletedOrNull()
    }
}