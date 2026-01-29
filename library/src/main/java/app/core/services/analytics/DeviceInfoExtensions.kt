package app.core.services.analytics

import app.core.services.deviceinfo.DeviceInfo
import app.core.services.deviceinfo.DevicePerformanceCategory
import java.math.RoundingMode

internal fun DeviceInfo.toAnalyticsProperties(): Map<String, Any> {
    return buildMap {
        put("logical_width_dp", logicalWidthDp)
        put("logical_height_dp", logicalHeightDp)
        put("display_density_dpi", displayDensityDpi)
        put("ram_category", ramCategory.toAnalyticsString())
        put("cpu_category", cpuCategory.toAnalyticsString())

        totalRamBytes?.let {
            put("total_ram_gb", it.bytesToGb())
        }

        availableStorageBytes?.let {
            put("available_storage_gb", it.bytesToGb())
        }

        totalStorageBytes?.let {
            put("total_storage_gb", it.bytesToGb())
        }
    }
}


private const val BYTES_IN_GIGABYTE = 1024.0 * 1024.0 * 1024.0

private fun Long.bytesToGb(): Double {
    return (this / BYTES_IN_GIGABYTE)
        .toBigDecimal()
        .setScale(2, RoundingMode.HALF_UP)
        .toDouble()
}

private fun DevicePerformanceCategory.toAnalyticsString(): String {
    return when (this) {
        DevicePerformanceCategory.VERY_WEAK -> "very weak"
        DevicePerformanceCategory.WEAK -> "weak"
        DevicePerformanceCategory.NORMAL -> "normal"
        DevicePerformanceCategory.POWERFUL -> "powerful"
        DevicePerformanceCategory.SUPER_POWERFUL -> "super powerful"
        DevicePerformanceCategory.UNKNOWN -> "unknown"
    }
}