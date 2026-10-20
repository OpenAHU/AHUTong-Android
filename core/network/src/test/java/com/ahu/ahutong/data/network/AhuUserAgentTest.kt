package com.ahu.ahutong.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.Request

class AhuUserAgentTest {
    @Test
    fun `ASCII versions and existing component suffixes stay unchanged`() {
        for (suffix in listOf("Android", "Android telemetry", "Android bootstrap-training")) {
            val original = "AHUTong/3.4.3-debug2 ($suffix)"
            assertEquals(original, acceptedHeader(original))
        }
    }

    @Test
    fun `localized version names are accepted by OkHttp`() {
        assertEquals("AHUTong/?????? (Android)", acceptedHeader("AHUTong/必吃榜测试服 (Android)"))
        assertEquals("AHUTong/3.4.3-? (Android)", acceptedHeader("AHUTong/3.4.3-β (Android)"))
    }

    @Test
    fun `controls cannot create invalid or additional headers`() {
        assertEquals("AHUTong/3.4.3???? (Android)", acceptedHeader("AHUTong/3.4.3\r\n\u0000\u007f (Android)"))
        assertEquals("AHUTong/3.4.3\tdebug (Android)", acceptedHeader("AHUTong/3.4.3\tdebug (Android)"))
    }

    private fun acceptedHeader(value: String): String? = Request.Builder()
        .url("https://example.test/")
        .header("User-Agent", asciiUserAgent(value))
        .build().header("User-Agent")
}
