package app.core.services.deviceinfo

import android.content.Context
import app.core.services.deviceinfo.classifier.DefaultDevicePerformanceClassifier
import app.core.services.deviceinfo.classifier.DefaultFlagshipDetector

internal object DeviceInfoProviderFactory {
    fun create(context: Context): DeviceInfoProvider {
        val dataSource = AndroidDeviceMetricsDataSource(context)
        val flagshipDetector = DefaultFlagshipDetector()
        val classifier = DefaultDevicePerformanceClassifier(flagshipDetector)
        return AndroidDeviceInfoProvider(dataSource, classifier)
    }
}