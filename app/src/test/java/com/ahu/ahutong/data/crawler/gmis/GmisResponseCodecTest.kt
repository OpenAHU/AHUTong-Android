package com.ahu.ahutong.data.crawler.gmis

import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class GmisResponseCodecTest {
    @Test fun decryptsIndependentAesFixtureUsingPublicRajaxKeyDerivation() {
        // Generated independently with .NET AES/ECB/PKCS7, not the Kotlin implementation.
        val result = GmisResponseCodec.decode("rINHq4ZMdQDc9mT9wcyaNkf9IFNc65gwZRqy6eIaf2E=")
        assertEquals(0, result.asJsonObject.getAsJsonArray("rows").size())
        assertEquals("Friday", result.asJsonObject.get("week").asString)
    }

    @Test fun acceptsPlainJsonAndQuotedCiphertext() {
        assertTrue(GmisResponseCodec.decode(" \uFEFF[] ").isJsonArray)
        assertTrue(GmisResponseCodec.decode("""{"rows":[]}""").isJsonObject)
        val quotedCipher = '"' + "rINHq4ZMdQDc9mT9wcyaNkf9IFNc65gwZRqy6eIaf2E=" + '"'
        assertTrue(GmisResponseCodec.decode(quotedCipher).isJsonObject)
    }

    @Test fun loginPagesAreReportedAsSessionExpiryRatherThanAnEmptyTimetable() {
        assertFailsWith<GmisSessionExpiredException> {
            GmisResponseCodec.decode("<html><title>登录</title><input type=password></html>")
        }
    }

    @Test fun malformedCiphertextEmptyBodiesAndScalarResponsesAreRejected() {
        for (body in listOf("", "???", "true", "-", "{broken", "AAAA")) {
            assertFailsWith<GmisProtocolException>(body) { GmisResponseCodec.decode(body) }
        }
    }
}
