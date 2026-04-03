package app.core.services.appupdates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A no-op implementation of [AppUpdateManager] that does nothing.
 * Useful for testing or for builds where update checks are disabled.
 */
internal class NoOpAppUpdateManager : AppUpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    override val updateState: StateFlow<UpdateState> = _updateState

    override fun setMinSupportedVersionCode(requiredVersion: Long) {
        // No-op
    }

    override fun cleanup() {
        // No-op
    }
}
