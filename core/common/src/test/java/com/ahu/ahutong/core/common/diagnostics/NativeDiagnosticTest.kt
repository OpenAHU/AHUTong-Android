package com.ahu.ahutong.core.common.diagnostics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeDiagnosticTest {
    private val buffer = DiagnosticBuffer()
    private val target = "ahutong_rs::data::crawler"

    @AfterTest fun clearPasswords() = DiagnosticSanitizer.clearKnownPasswords()

    @Test fun runtimeAndErrorsKeepTheirCategoriesAndDetailsRequireSelection() {
        val generation = buffer.currentGeneration()
        buffer.appendNative(generation, 3, target, "Fetching CAS login page")
        buffer.appendNative(generation, 2, target, "Request timed out\n  at fetch_schedule")
        val runtime = buffer.snapshot(DiagnosticCategory.RUNTIME)
        val errors = buffer.snapshot(DiagnosticCategory.ERROR)
        val details = buffer.snapshot(DiagnosticCategory.NETWORK)
        assertTrue(runtime.contains("Fetching CAS login page"))
        assertFalse(runtime.contains("Request timed out"))
        assertTrue(errors.contains("Request timed out"))
        assertTrue(errors.contains("at fetch_schedule"))
        assertFalse(errors.contains("Fetching CAS login page"))
        assertTrue(details.contains("Fetching CAS login page"))
        assertTrue(details.contains("Request timed out"))
    }

    @Test fun credentialsAreHiddenInOrdinaryLogsAndPasswordsNeverEnterAnyCategory() {
        DiagnosticSanitizer.rememberPassword("synthetic-unkeyed-password")
        val message = "Cookie: session=synthetic-cookie\nAuthorization: Bearer synthetic-token\n" +
            "password=synthetic-password&rsa=synthetic-derived&pl=12\n" +
            "lt=synthetic-lt execution=synthetic-execution name=synthetic-name\n" +
            "echo synthetic-unkeyed-password"
        buffer.appendNative(buffer.currentGeneration(), 1, target, message)
        val ordinary = buffer.snapshot(DiagnosticCategory.ERROR)
        listOf("synthetic-cookie", "synthetic-token", "synthetic-lt", "synthetic-execution", "synthetic-name")
            .forEach { assertFalse(ordinary.contains(it), ordinary) }
        val details = buffer.snapshot(DiagnosticCategory.NETWORK)
        assertTrue(details.contains("synthetic-cookie"))
        assertTrue(details.contains("synthetic-token"))
        DiagnosticCategory.entries.forEach { category ->
            val content = buffer.snapshot(category)
            listOf("synthetic-password", "synthetic-derived", "synthetic-unkeyed-password")
                .forEach { assertFalse(content.contains(it), content) }
        }
    }

    @Test fun nativePayloadsStayOutOfTheDefaultCategories() {
        val generation = buffer.currentGeneration()
        buffer.appendNative(generation, 3, target, "[RustSDKSchedule] HTML Head: <p>synthetic-person</p>")
        buffer.appendNative(generation, 1, target, "Server Error Body: synthetic-private-body")
        buffer.appendNative(generation, 2, target, "Parsing failed: {\"profile\":\"synthetic-profile\",\"password\":\"synthetic-pwd\"}")
        val ordinary = buffer.snapshot(DiagnosticCategory.RUNTIME) + buffer.snapshot(DiagnosticCategory.ERROR)
        listOf("synthetic-person", "synthetic-private-body", "synthetic-profile", "synthetic-pwd")
            .forEach { assertFalse(ordinary.contains(it), ordinary) }
        assertTrue(ordinary.contains("正文见网络详情"))
        val details = buffer.snapshot(DiagnosticCategory.NETWORK)
        assertTrue(details.contains("synthetic-person"))
        assertTrue(details.contains("synthetic-private-body"))
        assertTrue(details.contains("synthetic-profile"))
        assertFalse(details.contains("synthetic-pwd"))
    }

    @Test fun clearingTheAccountRejectsBothCategoriesOfLateNativeLogs() {
        val previous = buffer.currentGeneration()
        buffer.appendNative(previous, 3, target, "previous-account")
        buffer.clear()
        buffer.appendNative(previous, 1, target, "late-previous-account")
        DiagnosticCategory.entries.forEach { assertEquals("", buffer.snapshot(it)) }
        buffer.appendNative(buffer.currentGeneration(), 3, target, "current-account")
        assertTrue(buffer.snapshot(DiagnosticCategory.RUNTIME).contains("current-account"))
    }

    @Test fun oversizedNativeMessagesAreOmittedAndMarkedWithoutRetainingAPartialSecret() {
        buffer.appendNative(buffer.currentGeneration(), 3, target, "synthetic-secret" + "中".repeat(100_000))
        val details = buffer.snapshot(DiagnosticCategory.NETWORK)
        assertTrue(details.contains("超过256KiB，正文未采集"))
        assertFalse(details.contains("synthetic-secret"))
        assertTrue(details.toByteArray().size < 1024)
    }

    @Test fun unrelatedLibrariesAndInvalidLevelsCannotFillTheNativeRing() {
        buffer.appendNative(buffer.currentGeneration(), 0, target, "invalid-level")
        buffer.appendNative(buffer.currentGeneration(), 6, target, "invalid-level")
        buffer.appendNative(buffer.currentGeneration(), 1, "reqwest::connect", "unrelated-library")
        DiagnosticCategory.entries.forEach { assertEquals("", buffer.snapshot(it)) }
        buffer.appendNative(buffer.currentGeneration(), 3, "guixu::persistence", "cache-opened")
        assertTrue(buffer.snapshot(DiagnosticCategory.RUNTIME).contains("cache-opened"))
    }
}
