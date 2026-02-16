package app.core.services.attribution

interface DeviceIdProvider {
    fun provide(): String
}