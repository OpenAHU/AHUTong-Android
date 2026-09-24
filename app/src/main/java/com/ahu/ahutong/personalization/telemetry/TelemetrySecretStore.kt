package com.ahu.ahutong.personalization.telemetry

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ahu.ahutong.personalization.security.InvalidatedKeystoreKey
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetrySecretStore @Inject constructor() {
    fun createAndEncrypt(consentLifecycleId: String, secret: String): EncryptedTelemetrySecret {
        val alias = alias(consentLifecycleId)
        return InvalidatedKeystoreKey.retryWithFreshKey(alias) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(alias))
            val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(cipher.iv.size + ciphertext.size)
            cipher.iv.copyInto(combined)
            ciphertext.copyInto(combined, cipher.iv.size)
            EncryptedTelemetrySecret(alias, Base64.encodeToString(combined, Base64.NO_WRAP))
        }
    }

    fun decrypt(alias: String, encrypted: String): String? = InvalidatedKeystoreKey.readOrNull {
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        require(combined.size > IV_BYTES)
        val iv = combined.copyOfRange(0, IV_BYTES)
        val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val secretKey = keyStore.getKey(alias, null) as? SecretKey ?: return@readOrNull null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun delete(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun key(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun alias(consentLifecycleId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(consentLifecycleId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "ahu_model_telemetry_$digest"
    }

    data class EncryptedTelemetrySecret(val alias: String, val ciphertext: String)

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
