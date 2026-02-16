package app.core.services.appsflyer

import android.content.Context
import com.appsflyer.AppsFlyerLib

internal class DefaultAppsFlyerUidProvider(
    private val appsFlyer: AppsFlyerLib,
    private val applicationContext: Context,
) : AppsFlyerUidProvider {
    override fun get(): String? {
        return appsFlyer.getAppsFlyerUID(applicationContext)
    }
}