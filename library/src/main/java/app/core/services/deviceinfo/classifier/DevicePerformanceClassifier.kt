package app.core.services.deviceinfo.classifier

import app.core.services.deviceinfo.DevicePerformanceCategory

internal interface DevicePerformanceClassifier {
    fun ramCategory(totalRamBytes: Long?): DevicePerformanceCategory

    fun cpuCategory(
        cpuCores: Int?,
        primaryAbi: String?,
        fingerprintText: String?
    ): DevicePerformanceCategory
}