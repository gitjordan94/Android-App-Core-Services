package app.core.services.appupdates.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * The default implementation of [VersionProvider] that retrieves the version code.
 *
 * The result is cached after the first successful lookup for performance.
 */
internal class AppVersionProvider(private val context: Context) : VersionProvider {
    private val appVersionCode: Long? by lazy {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // This is the most likely and specific error.
            Timber.e(e, "Could not find package info for current app. This should not happen.")
            null
        } catch (e: Exception) {
            // A fallback for any other unexpected errors.
            Timber.e(e, "An unexpected error occurred while getting app version code")
            null
        }
    }

    /**
     * Returns the cached application version code.
     */
    override fun getVersionCode(): Long? = appVersionCode
}