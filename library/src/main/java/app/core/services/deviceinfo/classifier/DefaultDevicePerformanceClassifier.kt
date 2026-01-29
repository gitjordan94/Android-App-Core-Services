package app.core.services.deviceinfo.classifier

import app.core.services.deviceinfo.DevicePerformanceCategory

internal class DefaultDevicePerformanceClassifier(
    private val flagshipDetector: FlagshipDetector
) : DevicePerformanceClassifier {
    override fun ramCategory(totalRamBytes: Long?): DevicePerformanceCategory {
        val bytes = totalRamBytes ?: return DevicePerformanceCategory.UNKNOWN
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)

        return when {
            gb < 2.0 -> DevicePerformanceCategory.VERY_WEAK
            gb < 3.0 -> DevicePerformanceCategory.WEAK
            gb < 6.0 -> DevicePerformanceCategory.NORMAL
            gb < 12.0 -> DevicePerformanceCategory.POWERFUL
            else -> DevicePerformanceCategory.SUPER_POWERFUL
        }
    }

    override fun cpuCategory(
        cpuCores: Int?,
        primaryAbi: String?,
        fingerprintText: String?
    ): DevicePerformanceCategory {
        val cores = cpuCores ?: return DevicePerformanceCategory.UNKNOWN
        if (cores <= 0) return DevicePerformanceCategory.UNKNOWN

        val abi = primaryAbi.orEmpty()
        val isHighEndAbi = abi.contains("arm64-v8a") || abi.contains("armeabi-v7a")
        val isFlagship = flagshipDetector.isFlagship(fingerprintText.orEmpty())

        return when {
            cores < 4 -> DevicePerformanceCategory.VERY_WEAK
            cores == 4 && !isHighEndAbi -> DevicePerformanceCategory.WEAK
            cores in 4..6 && isHighEndAbi -> DevicePerformanceCategory.NORMAL
            cores >= 8 && isHighEndAbi && !isFlagship -> DevicePerformanceCategory.POWERFUL
            cores >= 8 && isFlagship -> DevicePerformanceCategory.SUPER_POWERFUL
            else -> DevicePerformanceCategory.NORMAL
        }
    }
}