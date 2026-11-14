package com.ahu.ahutong.assistant.model

import com.google.gson.JsonParser
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialTrialTest {
    @Test
    fun knownStudentNumberIsRedactedFromNestedMessageValues() {
        val input = JsonParser.parseString("""{"text":"学号 0099999999","values":["0099999999",12]}""")
        val actual = OfficialTrial.redactStudentNumber(input, "0099999999")
        assertEquals("""{"text":"学号 [学号已隐藏]","values":["[学号已隐藏]",12]}""", actual.toString())
    }

    @Test
    fun encryptedRegistrationIncludesRandomnessChallengeAndCompleteStudentNumber() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val base64 = Base64.getUrlEncoder().withoutPadding()
        val challenge = ByteArray(32) { 7 }
        val student = "0099999999"
        fun encrypt() = OfficialTrial.encryptStudentNumber(
            student,
            base64.encodeToString(challenge),
            base64.encodeToString(keyPair.public.encoded),
        )
        fun decrypt(encrypted: String): ByteArray = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                keyPair.private,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                    PSource.PSpecified("AHUTong AI trial v1".toByteArray())),
            )
            doFinal(Base64.getUrlDecoder().decode(encrypted))
        }

        val first = encrypt()
        val second = encrypt()
        assertNotEquals(first, second)
        val payload = decrypt(first)
        assertNotEquals(payload.take(16), decrypt(second).take(16))
        assertArrayEquals(challenge + student.toByteArray(), payload.copyOfRange(16, payload.size))
    }
}
