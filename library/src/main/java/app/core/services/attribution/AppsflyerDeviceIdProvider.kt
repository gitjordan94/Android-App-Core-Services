package app.core.services.attribution

import app.core.services.appsflyer.AppsFlyerAnalytics
import app.core.services.common.uuid.UUIDNamespace
import app.core.services.common.uuid.uuid5

internal class AppsflyerDeviceIdProvider(
    private val appsFlyerAnalytics: AppsFlyerAnalytics,
) : DeviceIdProvider {
    override fun provide(): String {
        val appsFlyerUID = appsFlyerAnalytics.appsFlyerUID
            ?: throw IllegalStateException("AppsFlyer UID is null")

        val uuid = uuid5(UUIDNamespace.OID, appsFlyerUID)

        return uuid.toString()
    }
}