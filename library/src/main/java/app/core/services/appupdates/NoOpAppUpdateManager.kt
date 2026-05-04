package app.core.services.appupdates

/**
 * A no-op implementation of [AppUpdateManager] that does nothing.
 * Useful for testing or for builds where update checks are disabled.
 */
internal class NoOpAppUpdateManager : AppUpdateManager {
    override fun setMinSupportedVersionCode(requiredVersion: Long) = Unit
    override fun cleanup() = Unit
}