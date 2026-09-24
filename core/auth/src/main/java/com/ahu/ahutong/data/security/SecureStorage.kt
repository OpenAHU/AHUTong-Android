package com.ahu.ahutong.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small fail-closed AES-GCM store backed by a non-exportable Android Keystore key.
 * Only ciphertext, IVs and version metadata are written to SharedPreferences.
 */
object SecureStorage {
    private const val TAG = "SecureStorage"
    private const val KEY_ALIAS = "ahutong.secure-storage.v1"
    private const val PREFS_NAME = "secure_storage_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION = "v1"
    private const val TAG_LENGTH_BITS = 128

    private val preferences by lazy {
        AppEnvironmentHolder.context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun putString(key: String, value: String) {
        if (value.isEmpty()) {
            remove(key)
            return
        }
        val encoded = try {
            encrypt(value)
        } catch (error: Exception) {
            if (!error.hasInvalidatedKeyCause()) throw error
            // The old ciphertext cannot be decrypted after Android invalidates its Keystore key.
            Log.w(TAG, "Keystore key invalidated; resetting encrypted values", error)
            resetInvalidatedKey()
            encrypt(value)
        }
        check(preferences.edit().putString(key, encoded).commit()) {
            "Failed to persist encrypted value"
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(
            VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        ).joinToString(":")
    }

    @Synchronized
    fun getString(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return try {
            val parts = encoded.split(':', limit = 3)
            require(parts.size == 3 && parts[0] == VERSION) { "Unsupported ciphertext" }
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(TAG_LENGTH_BITS, iv)
                )
            }
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            // A replaced/invalidated key must never make us fall back to treating ciphertext as data.
            Log.w(TAG, "Unable to decrypt stored value; removing it", e)
            if (e.hasInvalidatedKeyCause()) resetInvalidatedKey()
            else preferences.edit().remove(key).commit()
            null
        }
    }

    @Synchronized
    fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    @Synchronized
    fun entries(prefix: String): Map<String, String> =
        preferences.all.keys
            .asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull { key -> getString(key)?.let { value -> key to value } }
            .toMap()

    @Synchronized
    fun clearPrefix(prefix: String) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.commit()
    }

    private fun Throwable.hasInvalidatedKeyCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is KeyPermanentlyInvalidatedException) return true
            current = current.cause
        }
        return false
    }

    private fun resetInvalidatedKey() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(KEY_ALIAS)
        }
        check(preferences.edit().clear().commit()) {
            "Failed to clear values encrypted with invalidated key"
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
