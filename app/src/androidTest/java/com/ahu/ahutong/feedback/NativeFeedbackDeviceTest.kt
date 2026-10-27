package com.ahu.ahutong.feedback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.R
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import com.ahu.ahutong.sdk.RustSDK
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the installed .so and synthetic payloads; sends no Sentry events or campus requests. */
@RunWith(AndroidJUnit4::class)
class NativeFeedbackDeviceTest {
    @Test fun nativeLogsReachSelectedFeedbackAttachmentsAndEncryptedExports() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        RustSDK.loadLibrary(context)
        assertTrue("The installed native library must load", RustSDK.isNativeLoaded())
        val cookies = requireNotNull(RustSDK.dumpCookies())
        FeedbackDiagnostics.clear()
        try {
            // Restore exactly the current cookie snapshot; no login, HTTP request or account change.
            RustSDK.initSafe(cookies)
            assertTrue(RustSDK.isNativeLogBridgeActive())
            assertTrue("JNI must forward actual Rust logs, not just a Kotlin fixture",
                FeedbackDiagnostics.snapshot(DiagnosticCategory.RUNTIME).contains("Rust SDK Initialized"))
            val generation = FeedbackDiagnostics.currentGeneration()
            DiagnosticSanitizer.rememberPassword("synthetic-rust-password")
            RustSDK.captureNativeLog(generation, 1, "ahutong_rs::data::crawler",
                "Synthetic Rust parser failure\nCookie: session=synthetic-rust-cookie\n" +
                    "Authorization: Bearer synthetic-rust-token\npassword=synthetic-rust-password")
            val captured = mutableListOf<FeedbackAttachment>()
            val transport = object : FeedbackTransport {
                override fun captureError(details: String): String = error("No new error event expected")
                override fun captureFeedback(message: String, errorId: String?, attachments: List<FeedbackAttachment>): String {
                    captured += attachments
                    return "44444444444444444444444444444444"
                }
            }
            val coordinator = FeedbackCoordinator(transport, FeedbackDiagnostics::snapshot,
                { "synthetic Rust device validation" }, FeedbackDiagnostics::currentGeneration)
            coordinator.setDescription("合成 Rust 日志验证")
            coordinator.showPreview()
            assertFalse(coordinator.state.value.preview!!.contains("synthetic-rust-cookie"))
            coordinator.setCategory(DiagnosticCategory.NETWORK, true)
            coordinator.showPreview()
            assertTrue(coordinator.state.value.preview!!.contains("synthetic-rust-cookie"))
            assertFalse(coordinator.state.value.preview!!.contains("synthetic-rust-password"))
            coordinator.submit()
            assertTrue(captured.single { it.filename == "runtime.txt" }.text.contains("Rust SDK Initialized"))
            assertTrue(captured.single { it.filename == "errors.txt" }.text.contains("Synthetic Rust parser failure"))
            assertFalse(captured.single { it.filename == "errors.txt" }.text.contains("synthetic-rust-token"))
            assertTrue(captured.single { it.filename == "network.txt" }.text.contains("synthetic-rust-cookie"))
            assertFalse(captured.any { it.text.contains("synthetic-rust-password") })
            val plain = requireNotNull(coordinator.exportText())
            assertTrue(plain.contains("Rust SDK Initialized"))
            assertTrue(plain.contains("synthetic-rust-cookie"))
            val publicPem = context.resources.openRawResource(R.raw.feedback_export_public_key)
                .bufferedReader().use { it.readText() }
            val encrypted = FeedbackLogEncryption.encrypt(plain, publicPem)
            assertFalse(encrypted.toString(Charsets.UTF_8).contains("synthetic-rust-cookie"))
            File(context.getExternalFilesDir(null), "native-feedback-validation.ahulog").writeBytes(encrypted)
        } finally { FeedbackDiagnostics.clear() }
    }

    @Test fun callbacksFromAnEarlierAccountCannotRepopulateTheNativeBuffer() {
        FeedbackDiagnostics.clear()
        val previous = RustSDK.nativeLogGeneration()
        RustSDK.captureNativeLog(previous, 3, "ahutong_rs::core", "synthetic previous account")
        FeedbackDiagnostics.clear()
        try {
            RustSDK.captureNativeLog(previous, 1, "ahutong_rs::core", "synthetic late previous account")
            DiagnosticCategory.entries.forEach { assertEquals("", FeedbackDiagnostics.snapshot(it)) }
            RustSDK.captureNativeLog(RustSDK.nativeLogGeneration(), 3, "ahutong_rs::core", "synthetic current account")
            assertTrue(FeedbackDiagnostics.snapshot(DiagnosticCategory.RUNTIME).contains("synthetic current account"))
        } finally { FeedbackDiagnostics.clear() }
    }

    @Test fun localHttpLogsRespectTheGenerationStampedBeforeTheRequestWasQueued() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        RustSDK.loadLibrary(context)
        val cookies = requireNotNull(RustSDK.dumpCookies())
        val server = JsonParser.parseString(RustSDK.startServer(0)).asJsonObject
        org.junit.Assume.assumeFalse("This native build has no local HTTP server", server.has("error"))
        val client = AhuHttp.plain().build()
        try {
            fun initialize(generation: Long) {
                val body = Gson().toJson(mapOf("cookies_json" to cookies))
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("http://127.0.0.1:${server["port"].asInt}/init")
                    .header("X-AHUTONG-TOKEN", server["token"].asString)
                    .header("X-AHUTong-Diagnostic-Generation", generation.toString())
                    .post(body).build()
                client.newCall(request).execute().use { assertTrue(it.isSuccessful) }
            }
            val previous = FeedbackDiagnostics.currentGeneration()
            FeedbackDiagnostics.clear()
            initialize(previous)
            assertFalse(FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK).contains("[Rust "))
            initialize(FeedbackDiagnostics.currentGeneration())
            assertTrue("The HTTP handler must forward Rust records with its request's generation",
                FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK).contains("[Rust INFO ahutong_rs::"))
        } finally {
            RustSDK.stopServer()
            FeedbackDiagnostics.clear()
        }
    }
}
