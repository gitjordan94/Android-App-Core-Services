package app.core.services.appupdates

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.core.services.appupdates.util.AppVersionProvider
import app.core.services.appupdates.util.VersionProvider
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.system.exitProcess

internal class GoogleInAppUpdateManager(
    private val context: Context,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context),
    private val versionProvider: VersionProvider = AppVersionProvider(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : app.core.services.appupdates.AppUpdateManager {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    override val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // Non-null value acts as both flag and payload for a pending update.
    // Written from any thread (Remote Config callback), read on main thread.
    @Volatile
    private var pendingRequiredVersion: Long? = null

    // currentActivity is set only when activity is resumed, cleared on pause.
    private var currentActivity: ComponentActivity? = null

    // Separate tracking for which activity owns the launcher — persists across pause/resume.
    private var launcherOwnerActivity: ComponentActivity? = null
    private var appUpdateResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private var retryCount = 0

    private val activityLifecycleCallbacks = createActivityLifecycleCallbacks()

    init {
        (context.applicationContext as Application)
            .registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    override fun setMinSupportedVersionCode(requiredVersion: Long) {
        val currentVersion = versionProvider.getVersionCode()

        when {
            currentVersion == null -> {
                Timber.w("Unable to get current app version")
                _updateState.value = UpdateState.Error("Unable to determine app version")
            }

            requiredVersion <= currentVersion -> {
                Timber.i("No update required (min=$requiredVersion, current=$currentVersion)")
                _updateState.value = UpdateState.UpToDate(currentVersion)
            }

            else -> {
                Timber.w("Update required (min=$requiredVersion, current=$currentVersion)")
                _updateState.value = UpdateState.UpdateRequired(requiredVersion, currentVersion)
                pendingRequiredVersion = requiredVersion
                if (isReadyToUpdate()) requestAppUpdateInfo()
            }
        }
    }

    // Launcher must belong to the currently resumed activity — otherwise it's stale.
    private fun isReadyToUpdate(): Boolean =
        currentActivity != null
                && appUpdateResultLauncher != null
                && launcherOwnerActivity == currentActivity

    private fun checkForPendingUpdates() {
        if (pendingRequiredVersion != null) {
            if (isReadyToUpdate()) requestAppUpdateInfo()
        } else {
            checkOngoingUpdate()
        }
    }

    private fun createActivityLifecycleCallbacks() = object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is ComponentActivity) registerUpdateLauncher(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is ComponentActivity) {
                currentActivity = activity
                checkForPendingUpdates()
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity == currentActivity) currentActivity = null
        }

        override fun onActivityDestroyed(activity: Activity) {
            // currentActivity is already null here (cleared in onPause), so track the
            // launcher owner separately to avoid skipping cleanup on destroy.
            if (activity == launcherOwnerActivity) {
                appUpdateResultLauncher = null
                launcherOwnerActivity = null
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private fun registerUpdateLauncher(activity: ComponentActivity) {
        appUpdateResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            handleUpdateResult(result.resultCode)
        }
        launcherOwnerActivity = activity
    }

    private fun handleUpdateResult(resultCode: Int) {
        when (resultCode) {
            RESULT_CANCELED -> {
                Timber.w("User cancelled mandatory update — closing app")
                _updateState.value = UpdateState.UserCancelled
                currentActivity?.let(::closeApplication)
            }

            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Timber.w("Update failed, retry $retryCount/$MAX_RETRIES")
                    _updateState.value =
                        UpdateState.UpdateFailed("Update failed, retrying ($retryCount/$MAX_RETRIES)")
                    coroutineScope.launch {
                        delay(RETRY_DELAY_MS)
                        requestAppUpdateInfo()
                    }
                } else {
                    Timber.e("Update failed after $MAX_RETRIES retries")
                    retryCount = 0
                    _updateState.value =
                        UpdateState.UpdateFailed("Update failed after $MAX_RETRIES retries")
                }
            }

            else -> {
                retryCount = 0
                _updateState.value = UpdateState.UpdateInProgress
            }
        }
    }

    private fun closeApplication(activity: ComponentActivity) {
        try {
            activity.finishAffinity()
        } catch (e: Exception) {
            Timber.e(e, "Error during app exit")
        } finally {
            exitProcess(0)
        }
    }

    private fun checkOngoingUpdate() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    Timber.i("Resuming in-progress update")
                    _updateState.value = UpdateState.UpdateInProgress
                    startUpdateFlow(appUpdateInfo)
                }
            }
            .addOnFailureListener { error ->
                Timber.e(error, "Failed to check ongoing update")
                _updateState.value = UpdateState.Error("Failed to check update status")
            }
    }

    private fun requestAppUpdateInfo() {
        _updateState.value = UpdateState.CheckingForUpdates

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        Timber.i("Update available, starting flow")
                        _updateState.value = UpdateState.UpdateAvailable
                        startUpdateFlow(appUpdateInfo)
                    }

                    else -> {
                        Timber.i("No update available via Play Store")
                        // Play Store has no update — required version is unattainable via this path.
                        pendingRequiredVersion = null
                        _updateState.value = UpdateState.NoUpdateAvailable
                    }
                }
            }
            .addOnFailureListener { error ->
                Timber.e(error, "Failed to request update info")
                _updateState.value =
                    UpdateState.Error("Failed to check for updates: ${error.message}")
                // pendingRequiredVersion intentionally NOT cleared — retries on next resume.
            }
    }

    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo) {
        val launcher = appUpdateResultLauncher ?: run {
            Timber.e("Cannot start update flow — launcher is null")
            _updateState.value = UpdateState.Error("Update flow not ready")
            return
        }

        try {
            _updateState.value = UpdateState.StartingUpdate
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                launcher,
                AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start update flow")
            _updateState.value = UpdateState.Error("Failed to start update: ${e.message}")
        }
    }

    override fun cleanup() {
        try {
            (context.applicationContext as Application)
                .unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
            appUpdateResultLauncher = null
            launcherOwnerActivity = null
            currentActivity = null
            coroutineScope.cancel()
        } catch (e: Exception) {
            Timber.e(e, "Error during AppUpdateManager cleanup")
        }
    }

    private companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
    }
}