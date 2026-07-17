package app.core.services.billing.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal fun String.encryptAES(key: String, iv: String): String {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKeySpec = SecretKeySpec(key.toByteArray(), "AES")
    val ivSpec = IvParameterSpec(iv.toByteArray())
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)
    val encryptedBytes = cipher.doFinal(this.toByteArray())
    return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
}

internal fun String.decryptAES(key: String, iv: String): String {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val secretKeySpec = SecretKeySpec(key.toByteArray(), "AES")
    val ivSpec = IvParameterSpec(iv.toByteArray())
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)
    val decryptedBytes = cipher.doFinal(Base64.decode(this, Base64.NO_WRAP))
    return String(decryptedBytes)
}