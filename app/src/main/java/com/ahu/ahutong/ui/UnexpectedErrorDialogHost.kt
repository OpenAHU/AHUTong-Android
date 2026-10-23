package com.ahu.ahutong.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahu.ahutong.error.UnexpectedErrorReport
import com.ahu.ahutong.error.UnexpectedErrors
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle

private const val FEEDBACK_GROUP = "1006203134"

/** Composed inside each Activity's existing theme; never creates a window from a background Context. */
@Composable
internal fun UnexpectedErrorDialogHost() {
    val reports by UnexpectedErrors.queue.reports.collectAsState()
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val report = reports.firstOrNull() ?: return
    if (!resumed) return
    val context = LocalContext.current
    var feedbackHint by remember(report.id) { mutableStateOf<String?>(null) }
    UnexpectedErrorDialog(
        report = report,
        feedbackHint = feedbackHint,
        onDismiss = { UnexpectedErrors.queue.dismiss(report.id) },
        onCopy = {
            if (copyText(context, "AHUTong 错误报告", report.details)) {
                Toast.makeText(context, "错误信息已复制", Toast.LENGTH_SHORT).show()
            } else feedbackHint = "无法访问剪贴板，可长按下方错误信息选择复制。"
        },
        onFeedback = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$FEEDBACK_GROUP&card_type=group&source=qrcode"
            )).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val opened = runCatching { context.startActivity(intent) }.isSuccess
            if (!opened) {
                val copied = copyText(context, "AHUTong 反馈群", FEEDBACK_GROUP)
                feedbackHint = "请安装 QQ 或 TIM，搜索反馈群 $FEEDBACK_GROUP。" +
                    if (copied) "群号已复制。" else ""
            }
        }
    )
}

@Composable
internal fun UnexpectedErrorDialog(
    report: UnexpectedErrorReport,
    feedbackHint: String? = null,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onFeedback: () -> Unit
) {
    AppDialog(
        title = "意外错误",
        onDismiss = onDismiss,
        modifier = Modifier.heightIn(max = 600.dp),
        contentScrollable = true,
        headerContent = {
            Text("出现意料外的错误，请联系开发者。以下为错误信息", style = MaterialTheme.typography.bodyMedium)
            feedbackHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        actions = listOf(
            AppDialogAction("取消", onDismiss),
            AppDialogAction("复制", onCopy),
            AppDialogAction("反馈", onFeedback, AppDialogActionStyle.Primary)
        ),
        content = {
            SelectionContainer {
                Text(report.details, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

private fun copyText(context: Context, label: String, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}.isSuccess
