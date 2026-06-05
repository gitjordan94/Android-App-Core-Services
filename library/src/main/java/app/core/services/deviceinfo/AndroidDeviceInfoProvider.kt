package app.core.services.deviceinfo

import app.core.services.deviceinfo.classifier.DevicePerformanceClassifier

internal class AndroidDeviceInfoProvider(
    private val deviceMetricsDataSource: DeviceMetricsDataSource,
    private val classifier: DevicePerformanceClassifier
) : DeviceInfoProvider {
    override fun collectDeviceInfo(): DeviceInfo {
        val metrics = deviceMetricsDataSource.getMetrics()

        val density = (metrics.density ?: 1.0f).takeIf { it > 0f } ?: 1.0f
        val widthInDp = if ((metrics.widthPx ?: 0) > 0) {
            ((metrics.widthPx!! / density).toInt())
        } else {
            0
        }

        val heightInDp = if ((metrics.heightPx ?: 0) > 0) {
            ((metrics.heightPx!! / density).toInt())
        } else {
            0
        }

        val dpi = (metrics.densityDpi ?: 160).takeIf { it > 0 } ?: 160

        val ramCategory = classifier.ramCategory(metrics.totalRamBytes)

        val cpuCategory = classifier.cpuCategory(
            metrics.cpuCores,
            metrics.primaryAbi,
            metrics.buildFingerprintText
        )

        return DeviceInfo(
            logicalWidthDp = widthInDp,
            logicalHeightDp = heightInDp,
            displayDensityDpi = dpi,
            ramCategory = ramCategory,
            cpuCategory = cpuCategory,
            totalRamBytes = metrics.totalRamBytes,
            availableStorageBytes = metrics.availableStorageBytes,
            totalStorageBytes = metrics.totalStorageBytes,
        )
    }
}