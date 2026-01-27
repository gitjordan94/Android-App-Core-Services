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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Manages in-app updates for the application.
 *
 * This class handles:
 * - Version checking against Firebase Remote Config
 * - Immediate update flows through Google Play
 * - Activity lifecycle management
 * - Update state persistence across app sessions
 */
internal class AppUpdateManager(
    private val context: Context,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context),
    private val versionProvider: VersionProvider = AppVersionProvider(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    // State management
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // Thread-safe flags
    private val isInitialized = AtomicBoolean(false)
    private val hasPendingUpdate = AtomicBoolean(false)

    // Update tracking
    private var pendingRequiredVersion: Long? = null
    private var currentActivity: ComponentActivity? = null
    private var appUpdateResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    // Lifecycle management
    private val activityLifecycleCallbacks = createActivityLifecycleCallbacks()

    init {
        initialize()
    }

    private fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) {
            Timber.w("AppUpdateManager already initialized")
            return
        }

        registerActivityCallbacks()
        Timber.d("AppUpdateManager initialized successfully")
    }

    private fun registerActivityCallbacks() {
        val application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    /**
     * Sets the minimum supported version code and triggers update flow if needed.
     */
    fun setMinSupportedVersionCode(requiredVersion: Long) {
        val currentVersion = versionProvider.getVersionCode()

        when {
            currentVersion == null -> {
                Timber.w("Unable to get current app version")
                _updateState.value = UpdateState.Error("Unable to determine app version")
            }

            requiredVersion <= currentVersion -> {
                Timber.i("No update required (min: $requiredVersion, current: $currentVersion)")
                _updateState.value = UpdateState.UpToDate(currentVersion)
            }

            else -> {
                Timber.w("Update required! (min: $requiredVersion, current: $currentVersion)")
                _updateState.value = UpdateState.UpdateRequired(requiredVersion, currentVersion)

                pendingRequiredVersion = requiredVersion

                if (canStartUpdateFlow()) {
                    requestAppUpdateInfo()
                } else {
                    Timber.d("Activity not ready — scheduling pending update")
                    hasPendingUpdate.set(true)
                }
            }
        }
    }

    private fun canStartUpdateFlow(): Boolean {
        return currentActivity != null && appUpdateResultLauncher != null
    }

    private fun checkForPendingUpdates() {
        if (currentActivity == null) {
            return
        }

        if (hasPendingUpdate.get() && appUpdateResultLauncher != null) {
            Timber.i("Processing pending update (requiredVersion=$pendingRequiredVersion)")
            hasPendingUpdate.set(false)
            requestAppUpdateInfo()
        } else {
            checkOngoingUpdate()
        }
    }

    private fun createActivityLifecycleCallbacks() = object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            Timber.v("Activity created: ${activity.localClassName}")
            if (activity is ComponentActivity) {
                setupActivityForUpdates(activity)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            Timber.v("Activity resumed: ${activity.localClassName}")
            if (activity is ComponentActivity) {
                currentActivity = activity
                checkForPendingUpdates()
            }
        }

        override fun onActivityPaused(activity: Activity) {
            Timber.v("Activity paused: ${activity.localClassName}")
            if (activity == currentActivity) {
                currentActivity = null
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            Timber.v("Activity destroyed: ${activity.localClassName}")
            if (activity == currentActivity) {
                cleanupActivityResources()
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private fun setupActivityForUpdates(activity: ComponentActivity) {
        Timber.d("Setting up update launcher for: ${activity.localClassName}")

        appUpdateResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            handleUpdateResult(result.resultCode, activity)
        }

        currentActivity = activity
    }

    private fun handleUpdateResult(resultCode: Int, activity: ComponentActivity) {
        when (resultCode) {
            RESULT_CANCELED -> {
                Timber.w("User canceled mandatory update — closing app")
                _updateState.value = UpdateState.UserCancelled
                closeApplication(activity)
            }

            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                Timber.e("In-app update failed, retrying...")
                _updateState.value = UpdateState.UpdateFailed("Update failed, retrying")
                // Retry after a brief delay
                coroutineScope.launch {
                    delay(2000)
                    requestAppUpdateInfo()
                }
            }

            else -> {
                Timber.d("Update result code: $resultCode")
                _updateState.value = UpdateState.UpdateInProgress
            }
        }
    }

    private fun closeApplication(activity: ComponentActivity) {
        try {
            activity.finishAffinity()
        } catch (e: Exception) {
            Timber.e(e, "Error during graceful app exit")
        } finally {
            exitProcess(0)
        }
    }

    private fun cleanupActivityResources() {
        Timber.d("Cleaning up activity resources")
        appUpdateResultLauncher?.unregister()
        appUpdateResultLauncher = null
        currentActivity = null
    }

    private fun checkOngoingUpdate() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                logAppUpdateInfo(appUpdateInfo)

                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        Timber.i("Resuming in-progress update")
                        _updateState.value = UpdateState.UpdateInProgress
                        startUpdateFlow(appUpdateInfo)
                    }

                    else -> {
                        Timber.d("No ongoing update to resume")
                    }
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
                logAppUpdateInfo(appUpdateInfo)

                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        Timber.i("Update available, starting flow")
                        _updateState.value = UpdateState.UpdateAvailable
                        startUpdateFlow(appUpdateInfo)
                    }

                    UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                        Timber.i("No update available via Play Store")
                        _updateState.value = UpdateState.NoUpdateAvailable
                    }

                    else -> {
                        Timber.d("Update not available at this time")
                        _updateState.value = UpdateState.NoUpdateAvailable
                    }
                }
            }
            .addOnFailureListener { error ->
                Timber.e(error, "Failed to request app update info")
                _updateState.value =
                    UpdateState.Error("Failed to check for updates: ${error.message}")
            }
    }

    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo) {
        val launcher = appUpdateResultLauncher
        if (launcher == null) {
            Timber.e("Cannot start update flow — launcher is null")
            _updateState.value = UpdateState.Error("Update flow not ready")
            return
        }

        try {
            Timber.d("Starting immediate update flow")
            _updateState.value = UpdateState.StartingUpdate

            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                launcher,
                AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start update flow")
            _updateState.value =
                UpdateState.Error("Failed to start update: ${e.message}")
        }
    }

    private fun logAppUpdateInfo(appUpdateInfo: AppUpdateInfo) {
        val availability = when (appUpdateInfo.updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> "UPDATE_AVAILABLE"
            UpdateAvailability.UPDATE_NOT_AVAILABLE -> "UPDATE_NOT_AVAILABLE"
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> "DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS"
            else -> "UNKNOWN (${appUpdateInfo.updateAvailability()})"
        }

        Timber.d("Update availability: $availability")
    }

    /**
     * Clean up resources when the update manager is no longer needed.
     * Should be called when the application is being destroyed.
     */
    fun cleanup() {
        if (!isInitialized.get()) return

        try {
            (context.applicationContext as Application)
                .unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)

            cleanupActivityResources()
            Timber.d("AppUpdateManager cleaned up successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error during AppUpdateManager cleanup")
        }
    }
}