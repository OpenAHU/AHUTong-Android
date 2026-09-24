package com.ahu.ahutong.personalization.profile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ahu.ahutong.personalization.security.InvalidatedKeystoreKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileKeyManager @Inject constructor() {
    private val alias = "ahu_behavior_profile_hmac_v1"

    fun profileKey(accountIdentifier: String): String {
        require(accountIdentifier.isNotBlank())
        return InvalidatedKeystoreKey.retryWithFreshKey(alias) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(loadOrCreateKey())
            mac.doFinal(accountIdentifier.toByteArray(Charsets.UTF_8))
                .take(16)
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun loadOrCreateKey(): java.security.Key {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(alias, null)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }
}
