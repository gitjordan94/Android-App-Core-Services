package app.core.services.deviceinfo.classifier

internal interface FlagshipDetector {
    fun isFlagship(fingerprintText: String): Boolean
}