package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.settingsPrimaryTextStyle
import com.ahu.ahutong.ui.components.settingsSecondaryTextStyle

data class FeedbackUiState(
    val description: String = "",
    val selectedCategories: Set<DiagnosticCategory> = setOf(
        DiagnosticCategory.RUNTIME, DiagnosticCategory.ERROR
    ),
    val preview: String? = null,
    val submitting: Boolean = false,
    val status: String? = null,
    val feedbackId: String? = null,
    val exportStatus: String? = null
)

@Composable
fun FeedbackHubScreen(
    onFeedback: () -> Unit,
    onExport: () -> Unit,
    onOpenQq: () -> Unit,
    onBack: () -> Unit
) {
    FeedbackPage(title = "意见反馈", onBack = onBack) {
        AppSectionCard(contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SettingsActionRow(title = "问题反馈", leadingIcon = Icons.Outlined.Feedback,
                onClick = onFeedback)
            SettingsActionRow(title = "日志导出", leadingIcon = Icons.Outlined.Download,
                onClick = onExport)
            SettingsActionRow(title = "QQ反馈群", leadingIcon = Icons.Outlined.Forum,
                onClick = onOpenQq, showDivider = false)
        }
    }
}

@Composable
fun FeedbackScreen(
    state: FeedbackUiState,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (DiagnosticCategory, Boolean) -> Unit,
    onPreview: () -> Unit,
    onDismissPreview: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    FeedbackPage(title = "问题反馈", onBack = onBack) {
        AppTextField(
            value = state.description,
            onValueChange = { onDescriptionChange(it.take(4096)) },
            label = "问题描述",
            minLines = 5,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
                .testTag("feedback_description")
                .semantics { contentDescription = "问题描述" },
            enabled = !state.submitting,
            singleLine = false
        )
        if (state.description.length >= 3800) {
            Text("${state.description.length} / 4096", modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FeedbackLogChoices(state, onCategoryChange)
        AppButton(onClick = onPreview,
            modifier = Modifier.fillMaxWidth().testTag("feedback_preview"),
            enabled = !state.submitting) {
            Text("预览反馈")
        }
    }
    state.preview?.let { preview ->
        val dismiss = { if (!state.submitting) onDismissPreview() }
        AppDialog(
            title = "反馈预览",
            onDismiss = dismiss,
            modifier = Modifier.heightIn(max = 640.dp),
            contentScrollable = true,
            actions = listOf(
                AppDialogAction("关闭", dismiss, enabled = !state.submitting),
                AppDialogAction(if (state.submitting) "处理中" else "发送反馈", onSubmit,
                    AppDialogActionStyle.Primary,
                    enabled = !state.submitting && state.description.isNotBlank() && state.feedbackId == null)
            ),
            headerContent = { FeedbackStatus(state) },
            content = {
                SelectionContainer {
                    Text(preview, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
fun FeedbackExportScreen(
    state: FeedbackUiState,
    onCategoryChange: (DiagnosticCategory, Boolean) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    FeedbackPage(title = "日志导出", onBack = onBack) {
        FeedbackLogChoices(state, onCategoryChange)
        AppButton(onClick = onExport,
            modifier = Modifier.fillMaxWidth().testTag("feedback_export"),
            enabled = !state.submitting) {
            Text(if (state.submitting) "处理中" else "导出日志")
        }
        TextButton(onClick = onShare,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("feedback_share"),
            enabled = !state.submitting) {
            Text("分享日志")
        }
        state.exportStatus?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.testTag("feedback_export_status"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedbackPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize().imePadding()) { backdrop ->
        SettingsPageLayout(title = title, onBack = onBack, backdrop = backdrop, bottomPadding = 32.dp) {
            Column(modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
        }
    }
}

@Composable
private fun FeedbackLogChoices(state: FeedbackUiState, onCategoryChange: (DiagnosticCategory, Boolean) -> Unit) {
    AppSectionCard(contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        FeedbackLogChoice(title = "运行记录", selected = DiagnosticCategory.RUNTIME in state.selectedCategories,
            enabled = !state.submitting, onSelectedChange = { onCategoryChange(DiagnosticCategory.RUNTIME, it) })
        FeedbackLogChoice(title = "错误信息", selected = DiagnosticCategory.ERROR in state.selectedCategories,
            enabled = !state.submitting, onSelectedChange = { onCategoryChange(DiagnosticCategory.ERROR, it) })
        FeedbackLogChoice(title = "网络详情", description = "包含敏感信息",
            selected = DiagnosticCategory.NETWORK in state.selectedCategories,
            enabled = !state.submitting, onSelectedChange = { onCategoryChange(DiagnosticCategory.NETWORK, it) })
    }
}

@Composable
private fun FeedbackStatus(state: FeedbackUiState) {
    state.status?.takeIf { it.isNotBlank() }?.let {
        Text(it, modifier = Modifier.testTag("feedback_status"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.feedbackId?.let {
        SelectionContainer {
            Text("反馈编号：$it", style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun FeedbackLogChoice(
    title: String,
    description: String? = null,
    selected: Boolean,
    enabled: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = title }
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox,
                onValueChange = onSelectedChange)
            .heightIn(min = if (description == null) 64.dp else 80.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = settingsPrimaryTextStyle())
            description?.let {
                Text(it, style = settingsSecondaryTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
    }
}
