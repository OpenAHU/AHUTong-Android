package com.ahu.ahutong.data.xuexiaotong

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.Cookie

class ChaoxingCookieCodecTest {
    private val now = 1_000_000L

    @Test fun `version two round trip preserves every cookie attribute and distinct paths`() {
        val cookies = listOf(
            Cookie.Builder().name("session").value("a=b|c").hostOnlyDomain("mooc2-ans.chaoxing.com")
                .path("/mooc2-ans").expiresAt(now + 60_000).secure().httpOnly().sameSite("Lax").build(),
            Cookie.Builder().name("session").value("").domain("chaoxing.com").path("/").build()
        )

        assertEquals(cookies, ChaoxingCookieCodec.decode(ChaoxingCookieCodec.encode(cookies), now))
    }

    @Test fun `expired and deletion cookies do not return from storage`() {
        val cookies = listOf(
            Cookie.Builder().name("expired").value("x").domain("chaoxing.com").expiresAt(now).build(),
            Cookie.Builder().name("deleted").value("").domain("chaoxing.com").expiresAt(0).build()
        )
        assertTrue(ChaoxingCookieCodec.decode(ChaoxingCookieCodec.encode(cookies), now).isEmpty())
    }

    @Test fun `legacy representation migrates while preserving values with equals`() {
        val legacy = """{"passport2.chaoxing.com":"auth|chaoxing.com=a=b; uid|passport2.chaoxing.com=123"}"""
        val cookies = ChaoxingCookieCodec.decode(legacy, now)
        assertEquals(listOf("a=b", "123"), cookies.map { it.value })
        assertEquals(listOf("chaoxing.com", "passport2.chaoxing.com"), cookies.map { it.domain })
        assertTrue(cookies.all { it.path == "/" && !it.persistent })
        val migrated = ChaoxingCookieCodec.encode(cookies)
        assertTrue(migrated.contains("\"version\":2"))
        assertEquals(cookies, ChaoxingCookieCodec.decode(migrated, now))
    }

    @Test fun `malformed storage and unsupported versions are not treated as legacy cookies`() {
        assertTrue(ChaoxingCookieCodec.decode("{broken", now).isEmpty())
        assertTrue(ChaoxingCookieCodec.decode("""{"version":3,"cookies":[]}""", now).isEmpty())
    }
}
