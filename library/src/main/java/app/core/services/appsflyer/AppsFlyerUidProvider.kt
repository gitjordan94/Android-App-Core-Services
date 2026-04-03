package app.core.services.appsflyer

internal interface AppsFlyerUidProvider {
    fun get(): String?
}