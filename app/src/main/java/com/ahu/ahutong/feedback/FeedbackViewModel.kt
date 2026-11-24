package com.ahu.ahutong.feedback

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.R
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import com.ahu.ahutong.sdk.RustSDK
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object AppFeedbackDraft {
    private var existing: FeedbackCoordinator? = null
    val coordinator: FeedbackCoordinator
        @Synchronized get() = existing ?: create().also { existing = it }
    @Synchronized fun clearIfCreated() { existing?.clear() }
    private fun create() = FeedbackCoordinator(
        transport = SentryFeedbackTransport(),
        snapshot = FeedbackDiagnostics::snapshot,
        environment = {
            "AHUTong ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
                "设备 ${Build.MANUFACTURER} ${Build.MODEL}\n构建 ${BuildConfig.BUILD_TYPE}\n" +
                if (RustSDK.isNativeLogBridgeActive()) "Rust 日志：已连接"
                else "Rust 日志：尚未采集（原生库未支持或尚未初始化）"
        },
        generation = FeedbackDiagnostics::currentGeneration,
        snapshotPreSanitized = true
    )
}

class FeedbackViewModel : ViewModel() {
    private val coordinator = AppFeedbackDraft.coordinator
    val state = coordinator.state
    private var attached = false
    private var encryptedPickerExport: EncryptedFeedbackExport? = null
    var pendingExport: EncryptedFeedbackExport?
        // 读取时顺便丢弃已过期的导出，与 setter 的校验规则保持一致。
        get() = encryptedPickerExport?.takeIf(::isExportCurrent).also { encryptedPickerExport = it }
        set(value) {
            encryptedPickerExport = value?.takeIf(::isExportCurrent)
        }
    fun setDescription(value: String) = coordinator.setDescription(value)
    fun setCategory(category: DiagnosticCategory, selected: Boolean) = coordinator.setCategory(category, selected)
    fun attachError(details: String?, eventId: String?) {
        if (!attached) {
            attached = true
            coordinator.beginEntry(details, eventId)
        }
    }
    fun isExportCurrent(export: EncryptedFeedbackExport): Boolean = export.generation == FeedbackDiagnostics.currentGeneration()
    fun showPreview() {
        val request = coordinator.beginPreview() ?: return
        viewModelScope.launch(Dispatchers.Default) { coordinator.completePreview(request) }
    }
    fun dismissPreview() = coordinator.dismissPreview()
    fun reportExportStatus(message: String) = coordinator.reportExportStatus(message)
    fun submit() {
        val request = coordinator.beginSubmit() ?: return
        viewModelScope.launch(Dispatchers.IO) { coordinator.completeSubmit(request) }
    }

    internal fun beginExport(): FeedbackCoordinator.ExportRequest? = coordinator.beginExport()

    internal fun finishExportAction(generation: Long, message: String? = null) =
        coordinator.finishExportAction(generation, message)

    internal suspend fun createEncryptedExport(
        request: FeedbackCoordinator.ExportRequest,
        keepSubmittingOnSuccess: Boolean = false
    ): EncryptedFeedbackExport? = withContext(Dispatchers.IO) {
        val generation = request.generation
        try {
            val text = coordinator.buildExportText(request)
            val pem = AppEnvironmentHolder.context().resources.openRawResource(R.raw.feedback_export_public_key)
                .bufferedReader().use { it.readText() }
            val encrypted = FeedbackLogEncryption.encrypt(text, pem)
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
            if (coordinator.finishExport(true, generation, keepSubmitting = keepSubmittingOnSuccess)) {
                EncryptedFeedbackExport("AHUTong-logs-$stamp.ahulog", encrypted, generation)
            } else {
                null
            }
        } catch (error: Exception) {
            coordinator.finishExport(false, generation)
            if (error is CancellationException) throw error
            null
        }
    }
}
