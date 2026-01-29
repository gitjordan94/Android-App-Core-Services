package app.core.services.deviceinfo

internal data class DeviceInfo(
    val logicalWidthDp: Int,
    val logicalHeightDp: Int,
    val displayDensityDpi: Int,
    val ramCategory: DevicePerformanceCategory,
    val cpuCategory: DevicePerformanceCategory,
    val totalRamBytes: Long? = null,
    val availableStorageBytes: Long? = null,
    val totalStorageBytes: Long? = null
)