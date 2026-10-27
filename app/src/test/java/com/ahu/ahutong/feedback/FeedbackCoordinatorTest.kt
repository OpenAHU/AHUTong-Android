package com.ahu.ahutong.feedback

import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import org.junit.Assert.*
import org.junit.Test

class FeedbackCoordinatorTest {
    private class Transport : FeedbackTransport {
        var errors = 0
        var errorDetails: String? = null
        var failure = false
        var beforeCapture: () -> Unit = {}
        val feedbacks = mutableListOf<Triple<String, String?, List<FeedbackAttachment>>>()
        override fun captureError(details: String): String {
            errors++
            errorDetails = details
            return "11111111111111111111111111111111"
        }
        override fun captureFeedback(message: String, errorId: String?, attachments: List<FeedbackAttachment>): String? {
            beforeCapture()
            feedbacks += Triple(message, errorId, attachments)
            if (failure) throw IllegalStateException("synthetic transport failure")
            return "22222222222222222222222222222222"
        }
    }
    private var generation = 0L
    private val transport = Transport()
    private fun coordinator() = FeedbackCoordinator(transport, {
        when (it) {
            DiagnosticCategory.NETWORK -> "Cookie: session=synthetic-cookie\npassword=synthetic-password"
            DiagnosticCategory.ERROR -> "failed password=synthetic-password token=synthetic-token"
            DiagnosticCategory.RUNTIME -> "opened home"
        }
    }, { "AHUTong test environment" }, { generation })

    @Test fun defaultSubmissionOmitsNetworkAndAcceptsIndependentFeedback() {
        val coordinator = coordinator()
        coordinator.setDescription("页面无法打开")
        coordinator.submit()
        assertEquals(0, transport.errors)
        val captured = transport.feedbacks.single()
        assertNull(captured.second)
        assertEquals(listOf("runtime.txt", "errors.txt"), captured.third.map { it.filename })
        assertFalse(captured.third.joinToString { it.text }.contains("synthetic-password"))
        assertFalse(captured.third.joinToString { it.text }.contains("synthetic-token"))
        assertNotNull(coordinator.state.value.feedbackId)
        coordinator.submit()
        assertEquals(1, transport.feedbacks.size)
    }

    @Test fun retryReusesTheExactAssociatedErrorAndKeepsDraft() {
        val coordinator = coordinator()
        coordinator.attachError("parser failed password=synthetic-password", null)
        coordinator.setDescription("parser problem")
        transport.failure = true
        coordinator.submit()
        assertEquals("parser problem", coordinator.state.value.description)
        assertNull(coordinator.state.value.feedbackId)
        transport.failure = false
        coordinator.submit()
        assertEquals(1, transport.errors)
        assertEquals(2, transport.feedbacks.size)
        assertEquals(transport.feedbacks[0].second, transport.feedbacks[1].second)
        assertFalse(transport.errorDetails!!.contains("synthetic-password"))
    }

    @Test fun existingEventIdIsReusedAndUnselectedErrorDoesNotCreateOrAssociateAnEvent() {
        val coordinator = coordinator()
        coordinator.attachError("parser", "33333333333333333333333333333333")
        coordinator.setDescription("problem")
        coordinator.submit()
        assertEquals(0, transport.errors)
        assertEquals("33333333333333333333333333333333", transport.feedbacks.last().second)
        coordinator.setDescription("another problem")
        coordinator.setCategory(DiagnosticCategory.ERROR, false)
        coordinator.submit()
        assertNull(transport.feedbacks.last().second)
        assertFalse(transport.feedbacks.last().third.any { it.filename == "errors.txt" })
    }

    @Test fun networkIsExplicitAndPasswordsRemainMaskedInPreviewAndExport() {
        val coordinator = coordinator()
        assertNotNull(coordinator.exportText())
        coordinator.finishExport(true, generation)
        coordinator.setCategory(DiagnosticCategory.NETWORK, true)
        coordinator.showPreview()
        assertTrue(coordinator.state.value.preview!!.contains("synthetic-cookie"))
        assertFalse(coordinator.state.value.preview!!.contains("synthetic-password"))
        val exported = coordinator.exportText()!!
        assertTrue(exported.contains("synthetic-cookie"))
        assertFalse(exported.contains("synthetic-password"))
        coordinator.finishExport(true, generation)
    }

    @Test fun accountChangeDropsDraftErrorAndLateResults() {
        val coordinator = coordinator()
        coordinator.attachError("old account error", null)
        coordinator.setDescription("old account problem")
        transport.beforeCapture = { generation++ }
        coordinator.submit()
        assertEquals("", coordinator.state.value.description)
        assertNull(coordinator.state.value.feedbackId)
        assertFalse(coordinator.state.value.submitting)
        assertFalse(coordinator.finishExport(true, generation - 1))
    }

    @Test fun emptyMessageIsRejectedButLogExportNeedsNoMessage() {
        val coordinator = coordinator()
        coordinator.submit()
        assertTrue(transport.feedbacks.isEmpty())
        assertNotNull(coordinator.exportText())
        assertNull(coordinator.exportText())
    }

    @Test fun sendingUsesThePreviewedSnapshotAndKeepsPreviewVisible() {
        var record = "record before preview"
        val coordinator = FeedbackCoordinator(transport, { record }, { "test environment" }, { generation })
        coordinator.setDescription("problem")
        coordinator.showPreview()
        record = "new background record after preview"
        coordinator.submit()
        assertTrue(transport.feedbacks.single().third.all { it.text.contains("record before preview") })
        assertFalse(transport.feedbacks.single().third.any { it.text.contains("new background record") })
        assertNotNull(coordinator.state.value.preview)
        assertNotNull(coordinator.state.value.feedbackId)
        coordinator.dismissPreview()
        assertNull(coordinator.state.value.preview)
    }

    @Test fun exportContainsOnlyLogsAndDoesNotReplaceFeedbackStatus() {
        val coordinator = coordinator()
        coordinator.setDescription("A feedback draft that belongs to the other page")
        transport.failure = true
        coordinator.submit()
        val feedbackStatus = coordinator.state.value.status
        val exported = coordinator.exportText()!!
        assertFalse(exported.contains("A feedback draft that belongs to the other page"))
        coordinator.finishExport(true, generation)
        assertEquals(feedbackStatus, coordinator.state.value.status)
        assertNotNull(coordinator.state.value.exportStatus)
    }
}
