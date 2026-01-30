package app.core.services.deviceinfo

internal interface DeviceMetricsDataSource {
    fun getMetrics(): DeviceMetrics
}