package com.ahu.ahutong.core.common.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticBufferTest {
    private var now = 0L
    private fun buffer(maxBytes: Int = 2048) = DiagnosticBuffer(
        maxBytes = maxBytes, maxAgeMillis = 600_000, elapsedMillis = { now }, wallMillis = { 0 }
    )

    @Test fun categoriesUseDifferentPrivacyPoliciesBeforeEnteringTheRing() {
        val buffer = buffer()
        val generation = buffer.currentGeneration()
        val message = "Cookie: session=keep-session\npassword=never-store-password"
        buffer.append(DiagnosticCategory.RUNTIME, message, generation)
        buffer.append(DiagnosticCategory.ERROR, message, generation)
        buffer.append(DiagnosticCategory.NETWORK, message, generation)
        assertFalse(buffer.snapshot(DiagnosticCategory.RUNTIME).contains("keep-session"))
        assertFalse(buffer.snapshot(DiagnosticCategory.ERROR).contains("keep-session"))
        assertTrue(buffer.snapshot(DiagnosticCategory.NETWORK).contains("keep-session"))
        DiagnosticCategory.entries.forEach {
            assertFalse(buffer.snapshot(it).contains("never-store-password"))
        }
    }

    @Test fun recordsExpireAtTenMinutesWithoutNeedingAnotherAppend() {
        val buffer = buffer()
        buffer.append(DiagnosticCategory.RUNTIME, "old-operation", buffer.currentGeneration())
        now = 599_999
        assertTrue(buffer.snapshot(DiagnosticCategory.RUNTIME).contains("old-operation"))
        now = 600_000
        assertEquals("", buffer.snapshot(DiagnosticCategory.RUNTIME))
    }

    @Test fun capacityEvictsTheOldestEntryAcrossCategories() {
        val buffer = buffer(maxBytes = 180)
        val generation = buffer.currentGeneration()
        buffer.append(DiagnosticCategory.ERROR, "old-error-" + "a".repeat(80), generation)
        buffer.append(DiagnosticCategory.NETWORK, "new-network-" + "b".repeat(80), generation)
        assertEquals("", buffer.snapshot(DiagnosticCategory.ERROR))
        assertTrue(buffer.snapshot(DiagnosticCategory.NETWORK).contains("new-network"))
        assertTrue(buffer.snapshot(DiagnosticCategory.NETWORK).toByteArray().size <= 180)
    }

    @Test fun oversizedUnicodeRecordIsBoundedAndMarked() {
        val buffer = buffer(maxBytes = 160)
        buffer.append(DiagnosticCategory.RUNTIME, "中".repeat(1000), buffer.currentGeneration())
        val snapshot = buffer.snapshot(DiagnosticCategory.RUNTIME)
        assertTrue(snapshot.toByteArray().size <= 160)
        assertTrue(snapshot.contains("已截断"))
    }

    @Test fun clearRejectsCompletionOfAnEarlierAccountsInFlightRequest() {
        val buffer = buffer()
        val oldGeneration = buffer.currentGeneration()
        buffer.append(DiagnosticCategory.NETWORK, "old-account", oldGeneration)
        buffer.clear()
        buffer.append(DiagnosticCategory.NETWORK, "late-old-response", oldGeneration)
        assertEquals("", buffer.snapshot(DiagnosticCategory.NETWORK))
        buffer.append(DiagnosticCategory.NETWORK, "new-account", buffer.currentGeneration())
        assertTrue(buffer.snapshot(DiagnosticCategory.NETWORK).contains("new-account"))
    }

    @Test fun accountClearNotifiesDraftOwnerAndCannotBeBrokenByItsCallback() {
        var calls = 0
        FeedbackDiagnostics.setOnClearListener { calls++; error("draft failure") }
        try {
            FeedbackDiagnostics.recordRuntime("previous-account")
            DiagnosticSanitizer.rememberPassword("previous-password")
            FeedbackDiagnostics.clear()
            assertEquals(1, calls)
            assertEquals("", FeedbackDiagnostics.snapshot(DiagnosticCategory.RUNTIME))
            assertTrue(DiagnosticSanitizer.passwords("previous-password").contains("previous-password"))
        } finally { FeedbackDiagnostics.setOnClearListener(null) }
    }
}
