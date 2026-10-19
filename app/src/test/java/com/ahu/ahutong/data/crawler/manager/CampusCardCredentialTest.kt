package com.ahu.ahutong.data.crawler.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CampusCardCredentialTest {
    @Test
    fun `percent encoded opaque ticket is decoded once`() {
        assertEquals("opaque+value/=", credential("opaque%2Bvalue%2F%3D"))
        assertEquals("literal%2B%2F%3D", credential("literal%252B%252F%253D"))
        assertEquals("opaque%value", credential("opaque%25value"))
    }

    @Test
    fun `literal plus is preserved instead of form decoded to space`() {
        assertEquals("opaque+value", credential("opaque+value"))
        assertEquals("opaque+value+", credential("opaque+value%2B"))
    }

    @Test
    fun `unicode and encoded delimiters remain part of credential`() {
        assertEquals("opaque中&=#", credential("opaque%E4%B8%AD%26%3D%23"))
        assertEquals("opaque", credential("opaque#loginTransit"))
    }

    @Test
    fun `central CAS service and proxy tickets are not OAuth credentials`() {
        listOf("ST-one-shot", "%53%54-one-shot", "pt-one-shot").forEach {
            assertNull(credential(it))
        }
    }

    @Test
    fun `missing empty or malformed ticket is rejected`() {
        assertNull(TokenManager.extractCampusCardCredential("https://ycard.ahu.edu.cn/plat/?name=loginTransit"))
        listOf("", "%", "%2", "%GG").forEach { assertNull(credential(it)) }
    }

    private fun credential(rawTicket: String): String? =
        TokenManager.extractCampusCardCredential(
            "https://ycard.ahu.edu.cn/plat/?name=loginTransit&ticket=$rawTicket"
        )
}
