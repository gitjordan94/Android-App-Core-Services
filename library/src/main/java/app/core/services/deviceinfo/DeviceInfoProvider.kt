package app.core.services.deviceinfo

internal interface DeviceInfoProvider {
    fun collectDeviceInfo(): DeviceInfo
}