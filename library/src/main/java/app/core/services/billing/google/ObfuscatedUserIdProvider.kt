package app.core.services.billing.google

import app.core.services.billing.util.encryptAES
import app.core.services.billing.util.sha256
import timber.log.Timber

internal abstract class ObfuscatedUserIdProvider(
    private val secretKey: String,
    private val iv: String,
) {
    abstract fun provideUserId(): String?

    fun provideObfuscatedUserId(): ObfuscatedUserId? {
        val userId = provideUserId() ?: return null

        if (secretKey.isEmpty() || iv.isEmpty()) {
            return null
        }

        return try {
            ObfuscatedUserId(
                userId = userId,
                obfuscatedAccountId = userId.sha256(),
                obfuscatedProfileId = userId.encryptAES(secretKey, iv)
            )
        } catch (e: Throwable) {
            Timber.e(e)
            null
        }
    }
}

internal data class ObfuscatedUserId(
    val userId: String,
    val obfuscatedAccountId: String,
    val obfuscatedProfileId: String
)