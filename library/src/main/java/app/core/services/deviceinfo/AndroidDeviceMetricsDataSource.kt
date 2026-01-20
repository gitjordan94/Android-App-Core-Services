package app.core.services.deviceinfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.DisplayMetrics
import androidx.core.content.getSystemService
import timber.log.Timber

internal class AndroidDeviceMetricsDataSource(
    private val context: Context,
) : DeviceMetricsDataSource {
    override fun getMetrics(): DeviceMetrics {
        Timber.d("Starting device metrics collection")

        val display = getDisplayMetrics()
        val ram = getTotalRamBytes()
        val storage = getStorageInfo()
        val cores = availableCpuCores()
        val abi = getDeviceAbi()
        val fingerprintText = buildFingerprintText()

        val metrics = DeviceMetrics(
            widthPx = display?.widthPixels,
            heightPx = display?.heightPixels,
            density = display?.density,
            densityDpi = display?.densityDpi,
            totalRamBytes = ram,
            availableStorageBytes = storage?.first,
            totalStorageBytes = storage?.second,
            cpuCores = cores,
            primaryAbi = abi,
            buildFingerprintText = fingerprintText,
        )

        Timber.d("Collected device metrics: %s", metrics)

        return metrics
    }

    private fun getDisplayMetrics(): DisplayMetrics? {
        return try {
            context.resources.displayMetrics.takeIf {
                it.widthPixels > 0 && it.heightPixels > 0 && it.density > 0f
            } ?: run {
                Timber.w("DisplayMetrics invalid: %s", context.resources.displayMetrics)
                null
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to read DisplayMetrics")
            null
        }
    }

    private fun getTotalRamBytes(): Long? {
        return try {
            val activityManager = context.getSystemService<ActivityManager>()
            if (activityManager == null) {
                Timber.w("ActivityManager is null, cannot read RAM info")
                return null
            }

            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)

            info.totalMem.takeIf { it > 0 } ?: run {
                Timber.w("Total RAM reported as invalid: %d", info.totalMem)
                null
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to read total RAM")
            null
        }
    }

    private fun getStorageInfo(): Pair<Long, Long>? = try {
        val stat = StatFs(context.filesDir.absolutePath)
        val blockSize = stat.blockSizeLong
        val available = stat.availableBlocksLong * blockSize
        val total = stat.blockCountLong * blockSize

        if (available >= 0 && total > 0) {
            Pair(available, total)
        } else {
            Timber.w(
                "Invalid storage values: available=%d, total=%d",
                available,
                total
            )
            null
        }
    } catch (e: Throwable) {
        Timber.e(e, "Failed to read storage info")
        null
    }

    private fun availableCpuCores(): Int? {
        return try {
            Runtime.getRuntime().availableProcessors().takeIf { it > 0 } ?: run {
                Timber.w("availableProcessors returned invalid value")
                null
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to read CPU core count")
            null
        }
    }

    private fun getDeviceAbi(): String? {
        return try {
            Build.SUPPORTED_ABIS.firstOrNull() ?: run {
                Timber.w("SUPPORTED_ABIS is empty")
                null
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to read device ABI")
            null
        }
    }

    private fun buildFingerprintText(): String {
        val fingerprint = buildString {
            append(Build.HARDWARE.orEmpty().lowercase())
            append(' ')
            append(Build.BOARD.orEmpty().lowercase())
            append(' ')
            append(Build.DEVICE.orEmpty().lowercase())
        }

        Timber.d("Device fingerprint text: %s", fingerprint)
        return fingerprint
    }
}