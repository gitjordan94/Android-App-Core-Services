package app.core.services.attribution.installreferrer

internal interface InstallReferrerProvider {
    suspend fun getInstallReferrer(): InstallReferrerDetails?
}