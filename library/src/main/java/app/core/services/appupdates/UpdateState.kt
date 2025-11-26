package app.core.services.appupdates

/**
 * Represents the current state of app updates.
 */
internal sealed class UpdateState {
    object Unknown : UpdateState()
    object CheckingForUpdates : UpdateState()
    object NoUpdateAvailable : UpdateState()
    object UpdateAvailable : UpdateState()
    object StartingUpdate : UpdateState()
    object UpdateInProgress : UpdateState()
    object UserCancelled : UpdateState()

    data class UpToDate(val currentVersion: Long) : UpdateState()

    data class UpdateRequired(
        val requiredVersion: Long,
        val currentVersion: Long
    ) : UpdateState()

    data class UpdateFailed(val reason: String) : UpdateState()

    data class Error(val message: String) : UpdateState()
}