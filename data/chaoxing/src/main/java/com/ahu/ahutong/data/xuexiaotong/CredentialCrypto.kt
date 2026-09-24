package com.ahu.ahutong.data.xuexiaotong

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CredentialCrypto {
    private const val ALIAS = "ahutong_credential_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun encrypt(plain: String): String = try {
        encryptWithCurrentKey(plain)
    } catch (error: Exception) {
        if (!error.hasInvalidatedKeyCause()) throw error
        KeyStore.getInstance(KEYSTORE).apply {
            load(null)
            deleteEntry(ALIAS)
        }
        encryptWithCurrentKey(plain)
    }

    private fun encryptWithCurrentKey(plain: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    private fun Throwable.hasInvalidatedKeyCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is KeyPermanentlyInvalidatedException) return true
            current = current.cause
        }
        return false
    }

    fun decrypt(data: String): String? {
        return try {
            val bytes = Base64.decode(data, Base64.NO_WRAP)
            if (bytes.size <= IV_LEN) {
                null
            } else {
                val key = getOrCreateKey()
                val cipher = Cipher.getInstance(TRANSFORM)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_LEN))
                )
                String(cipher.doFinal(bytes.copyOfRange(IV_LEN, bytes.size)), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }
}
