package com.ahu.ahutong.feedback

import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackExportPickerStateTest {
    @Test fun takingThePickerResultPreservesTheEncryptedBytesForSaving() {
        val viewModel = FeedbackViewModel()
        val ciphertext = "synthetic encrypted log export".toByteArray()
        viewModel.pendingExport = EncryptedFeedbackExport(
            "synthetic.ahulog", ciphertext.copyOf(), FeedbackDiagnostics.currentGeneration()
        )

        val result = requireNotNull(viewModel.pendingExport)
        viewModel.pendingExport = null

        assertArrayEquals(ciphertext, result.bytes)
        assertNull(viewModel.pendingExport)
    }

    @Test fun changingAccountsInvalidatesPickerResultsAndCannotRestampOldExports() {
        val viewModel = FeedbackViewModel()
        val export = EncryptedFeedbackExport(
            "synthetic.ahulog", byteArrayOf(1, 2, 3), FeedbackDiagnostics.currentGeneration()
        )
        viewModel.pendingExport = export
        assertTrue(viewModel.isExportCurrent(export))

        FeedbackDiagnostics.clear()

        assertFalse(viewModel.isExportCurrent(export))
        assertNull(viewModel.pendingExport)
        viewModel.pendingExport = export
        assertNull("Old ciphertext must not acquire the new account generation", viewModel.pendingExport)
    }
}
