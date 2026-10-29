package com.ahu.ahutong.feedback

import com.google.gson.JsonParser
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class FeedbackLogEncryptionTest {
    @Test fun roundTripAuthenticatesEnvelopeAndUsesFreshRandomness() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val pem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(keys.public.encoded)}\n-----END PUBLIC KEY-----"
        val text = "用户问题\nCookie: synthetic-cookie-value"
        val encrypted = FeedbackLogEncryption.encrypt(text, pem)
        assertFalse(String(encrypted).contains("synthetic-cookie-value"))
        assertFalse(encrypted.contentEquals(FeedbackLogEncryption.encrypt(text, pem)))
        val json = JsonParser.parseString(String(encrypted)).asJsonObject
        val decoder = Base64.getDecoder()
        val key = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
            init(Cipher.DECRYPT_MODE, keys.private, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            doFinal(decoder.decode(json["wrappedKey"].asString))
        }
        fun decrypt(ciphertext: ByteArray): String = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, decoder.decode(json["nonce"].asString)))
            updateAAD("${json["format"].asString}|${json["algorithm"].asString}|${json["keyId"].asString}".toByteArray())
            String(doFinal(ciphertext), Charsets.UTF_8)
        }
        val ciphertext = decoder.decode(json["ciphertext"].asString)
        assertEquals(text, decrypt(ciphertext))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) { decrypt(ciphertext) }
    }
}
