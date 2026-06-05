package app.core.services.billing.util

import android.util.Base64
import java.security.MessageDigest

/**
 * Computes the SHA-256 hash of a string and returns it as a Base64 encoded string.
 *
 * @return The Base64 encoded SHA-256 hash of the string.
 */
internal fun String.sha256(): String {
    val bytes = this.toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(bytes)
    return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
}