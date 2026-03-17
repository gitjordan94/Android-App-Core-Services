package app.core.services.appupdates

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing in-app updates.
 */
internal interface AppUpdateManager {
    /**
     * The current state of the update process.
     */
    val updateState: StateFlow<UpdateState>

    /**
     * Sets the minimum supported version code and triggers the update flow if necessary.
     */
    fun setMinSupportedVersionCode(requiredVersion: Long)

    /**
     * Cleans up resources used by the update manager.
     */
    fun cleanup()
}
