package app.core.services.attribution.installreferrer

internal data class InstallReferrerDetails(
    val installReferrer: String?,
    val referrerClickTimestampSeconds: Long?,
    val installBeginTimestampSeconds: Long?,
    val googlePlayInstant: Boolean?,
    val referrerClickTimestampServerSeconds: Long?,
    val installBeginTimestampServerSeconds: Long?,
    val installVersion: String?,
)