package com.ahu.ahutong.error

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnexpectedErrorReportTest {
    @Test
    fun `original exception causes suppressed failures and source lines survive`() {
        val root = NullPointerException("missing response field")
        root.stackTrace = arrayOf(StackTraceElement("example.Parser", "parse", "Parser.kt", 42))
        val failure = IllegalStateException("decode failed", root)
        failure.addSuppressed(IllegalArgumentException("cleanup failed"))

        val report = UnexpectedErrorReportFormatter.format(failure, "AHUTong 3.4.5 (304009)")

        assertTrue(report.contains("AHUTong 3.4.5 (304009)"))
        assertTrue(report.contains("Caused by: java.lang.NullPointerException"))
        assertTrue(report.contains("Suppressed: java.lang.IllegalArgumentException"))
        assertTrue(report.contains("Parser.kt:42"))
    }

    @Test
    fun `credentials are hidden everywhere in original and nested messages`() {
        val failure = IllegalStateException(
            "GET https://name:private-password@ahu.edu.cn/mail?sid=secret-session&token=secret-token#private-fragment\n" +
                "Cookie: JSESSIONID=private-cookie; mail_sid=private-mail\n" +
                "Authorization: Bearer private-bearer\n" +
                "{\"password\":\"private password\",\"access_token\":\"private-access\",\"username\":\"private-user\"}\n" +
                "account=private-account xh=W000000001 someone@stu.ahu.edu.cn",
            IllegalArgumentException("sid=nested-session pwd=nested-password ticket=nested-ticket")
        )
        failure.addSuppressed(IllegalStateException("refresh_token=suppressed-token"))

        val report = UnexpectedErrorReportFormatter.format(failure, "Android 16 / API 36")

        listOf("private-password", "secret-session", "secret-token", "private-fragment", "private-cookie",
            "private-mail", "private-bearer", "private password", "private-access", "private-user", "private-account",
            "W000000001", "someone@stu.ahu.edu.cn", "nested-session", "nested-password", "nested-ticket",
            "suppressed-token").forEach { assertFalse(report.contains(it), "Leaked $it") }
        assertTrue(report.contains("ahu.edu.cn/mail"))
        assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("[已隐藏]"))
    }

    @Test
    fun `malformed urls are not copied verbatim`() {
        val safe = UnexpectedErrorReportFormatter.sanitize("request https://user:password@%ZZ/?sid=private-session")
        assertFalse(safe.contains("password"))
        assertFalse(safe.contains("private-session"))
    }

    @Test
    fun `nested payload fields and cookie arrays are hidden`() {
        val failure = IllegalStateException(
            "payload={\"data\":{\"token\":\"payload-secret\",\"sid\":\"payload-session\"}," +
                "\"cookies\":[\"secret-one\",\"secret-two\"]," +
                "\"url\":\"https://ahu.edu.cn/entry?anything=private-query\"}"
        )
        val report = UnexpectedErrorReportFormatter.format(failure, "AHUTong")
        listOf("payload-secret", "payload-session", "secret-one", "secret-two", "private-query")
            .forEach { assertFalse(report.contains(it), "Leaked $it") }
    }

    @Test
    fun `very large stack traces are bounded before retention`() {
        val failure = IllegalStateException("password=" + "a".repeat(100_000))
        val report = UnexpectedErrorReportFormatter.format(failure, "AHUTong")
        assertTrue(report.length < 50_000)
        assertTrue(report.contains("[追踪过长，已截断]"))
        assertFalse(report.contains("a".repeat(100)))
    }
}
