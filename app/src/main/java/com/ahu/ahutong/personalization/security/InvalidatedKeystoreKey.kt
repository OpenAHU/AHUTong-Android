package com.ahu.ahutong.personalization.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException

internal object InvalidatedKeystoreKey {
    @Synchronized
    fun <T> retryWithFreshKey(alias: String, action: () -> T): T = try {
        action()
    } catch (error: Exception) {
        if (!error.hasInvalidatedKeyCause()) throw error
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
        action()
    }

    fun <T> readOrNull(action: () -> T): T? = try {
        action()
    } catch (error: Exception) {
        if (!error.hasUnreadableKeyCause()) throw error
        null // Old ciphertext cannot be recovered without its original key.
    }

    private fun Throwable.hasInvalidatedKeyCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is KeyPermanentlyInvalidatedException) return true
            current = current.cause
        }
        return false
    }

    private fun Throwable.hasUnreadableKeyCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is KeyPermanentlyInvalidatedException ||
                current is UnrecoverableKeyException ||
                current is BadPaddingException
            ) return true
            current = current.cause
        }
        return false
    }
}
