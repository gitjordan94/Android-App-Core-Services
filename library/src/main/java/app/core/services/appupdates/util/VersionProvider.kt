package app.core.services.appupdates.util

/**
 * A functional interface for providing the app's version code.
 *
 * Using a `fun interface` allows for simple, lambda-based instantiation, which is ideal for testing.
 */
internal interface VersionProvider {
    /**
     * @return The application's version code as a [Long], or `null` if it cannot be retrieved.
     */
    fun getVersionCode(): Long?
}