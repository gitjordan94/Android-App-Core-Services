package app.core.services.appupdates

/**
 * Interface for managing in-app updates.
 */
internal interface AppUpdateManager {
    /**
     * Sets the minimum supported version code and triggers the update flow if necessary.
     */
    fun setMinSupportedVersionCode(requiredVersion: Long)

    /**
     * Cleans up resources used by the update manager.
     */
    fun cleanup()
}