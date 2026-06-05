package app.core.services.deviceinfo

internal data class DeviceMetrics(
    val widthPx: Int?,
    val heightPx: Int?,
    val density: Float?,
    val densityDpi: Int?,
    val totalRamBytes: Long?,
    val availableStorageBytes: Long?,
    val totalStorageBytes: Long?,
    val cpuCores: Int?,
    val primaryAbi: String?,
    val buildFingerprintText: String?,
)