package com.ahu.ahutong.feedback

import com.google.gson.Gson
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

data class EncryptedFeedbackExport(val filename: String, val bytes: ByteArray, val generation: Long)

/** Hybrid encryption: the private RSA key never ships in the application. */
internal object FeedbackLogEncryption {
    private const val FORMAT = "AHUTONG-LOG-1"
    private const val ALGORITHM = "RSA-OAEP-SHA256+A256GCM"

    fun encrypt(text: String, publicPem: String): ByteArray {
        val publicDer = Base64.getMimeDecoder().decode(publicPem
            .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicDer))
        val keyId = MessageDigest.getInstance("SHA-256").digest(publicDer)
            .joinToString("") { "%02x".format(it.toInt() and 255) }.take(16)
        val random = SecureRandom()
        val aesKey = ByteArray(32).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        try {
            val aad = "$FORMAT|$ALGORITHM|$keyId".toByteArray(Charsets.UTF_8)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(text.toByteArray(Charsets.UTF_8))
            }
            val wrapped = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
                init(Cipher.ENCRYPT_MODE, publicKey, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
                doFinal(aesKey)
            }
            val encoder = Base64.getEncoder()
            return Gson().toJson(linkedMapOf(
                "format" to FORMAT, "algorithm" to ALGORITHM, "keyId" to keyId,
                "wrappedKey" to encoder.encodeToString(wrapped),
                "nonce" to encoder.encodeToString(nonce), "ciphertext" to encoder.encodeToString(encrypted)
            )).toByteArray(Charsets.UTF_8)
        } finally { aesKey.fill(0) }
    }
}
