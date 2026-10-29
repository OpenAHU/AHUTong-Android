package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.pack.DialogStyle
import com.ahu.ahutong.ui.theme.pack.LocalComponentPack
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 弹窗底部胶囊按钮的风格。 */
enum class AppDialogActionStyle { Neutral, Primary, Danger }

/** 弹窗底部动作：文案 + 回调 + 风格。点击不自动关闭弹窗（校验失败等场景由回调自行决定）。 */
data class AppDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val style: AppDialogActionStyle = AppDialogActionStyle.Neutral,
    val enabled: Boolean = true
)

/**
 * 按当前弹窗槽位选择样式，保持相同的内容与动作接口。
 * 窗口属性由平台 Dialog 执行，支付/更新等场景可独立禁止返回或外部点击。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    titleFontWeight: FontWeight? = FontWeight.SemiBold,
    titleMaxLines: Int = Int.MAX_VALUE,
    contentScrollable: Boolean = false,
    contentSpacing: Dp = 14.dp,
    properties: DialogProperties = DialogProperties(),
    actions: List<AppDialogAction> = emptyList(),
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val nativeTitleStyle = if (titleStyle == MaterialTheme.typography.headlineMedium) {
        MaterialTheme.typography.headlineSmall
    } else titleStyle
    when (LocalComponentPack.current.dialogStyle) {
        DialogStyle.Material -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = modifier,
                properties = properties,
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, style = nativeTitleStyle, fontWeight = titleFontWeight,
                            maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
                        headerContent?.invoke(this)
                    }
                },
                text = if (subtitle != null || content != null) {
                    {
                        Column(
                            modifier = if (contentScrollable) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else Modifier,
                            verticalArrangement = Arrangement.spacedBy(contentSpacing)
                        ) {
                            subtitle?.let { Text(it) }
                            content?.invoke(this)
                        }
                    }
                } else null,
                confirmButton = {
                    if (actions.size > 2) {
                        FlowRow(horizontalArrangement = Arrangement.End) {
                            actions.forEach { MaterialDialogAction(it) }
                        }
                    } else actions.lastOrNull()?.let { MaterialDialogAction(it) }
                },
                dismissButton = if (actions.size == 2) {
                    {
                        MaterialDialogAction(actions.first())
                    }
                } else null
            )
        }
        DialogStyle.Miuix -> {
            Dialog(onDismissRequest = onDismiss, properties = properties) {
                MiuixCard(
                    modifier = modifier,
                    cornerRadius = 32.dp,
                    insideMargin = PaddingValues(24.dp),
                    colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            title,
                            modifier = Modifier.fillMaxWidth(),
                            style = nativeTitleStyle,
                            fontWeight = titleFontWeight,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        subtitle?.let {
                            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        headerContent?.invoke(this)
                        if (content != null) {
                            Column(
                                modifier = if (contentScrollable) {
                                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                                } else Modifier,
                                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                                content = content
                            )
                        }
                        if (actions.isNotEmpty()) {
                            // Stacking long action lists preserves usable touch targets at large font sizes.
                            if (actions.size > 2) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    actions.forEach { MiuixDialogAction(it, Modifier.fillMaxWidth()) }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    actions.forEach { MiuixDialogAction(it, Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        DialogStyle.Radiant -> RadiantDialog(
            title, onDismiss, modifier, subtitle, titleStyle, titleFontWeight, titleMaxLines,
            contentScrollable, contentSpacing, properties, actions, headerContent, content
        )
    }
}

@Composable
private fun MaterialDialogAction(action: AppDialogAction) {
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (action.style == AppDialogActionStyle.Danger) {
                MaterialTheme.colorScheme.error
            } else MaterialTheme.colorScheme.primary
        )
    ) { Text(action.label) }
}

@Composable
private fun MiuixDialogAction(action: AppDialogAction, modifier: Modifier) {
    MiuixButtonImpl(
        onClick = action.onClick,
        modifier = modifier,
        enabled = action.enabled,
        variant = when (action.style) {
            AppDialogActionStyle.Neutral -> AppButtonVariant.Secondary
            AppDialogActionStyle.Primary -> AppButtonVariant.Primary
            AppDialogActionStyle.Danger -> AppButtonVariant.Destructive
        }
    ) { Text(action.label) }
}

/** Shared window surface for callers which already own their dialog's title and actions. */
@Composable
internal fun ThemedDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    properties: DialogProperties,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val surfaceModifier = modifier.fillMaxWidth(0.9f).widthIn(max = AppComponentTokens.DialogMaxWidth)
        when (LocalComponentPack.current.dialogStyle) {
            DialogStyle.Miuix -> MiuixCard(
                modifier = surfaceModifier,
                cornerRadius = 32.dp,
                insideMargin = PaddingValues(0.dp),
                colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                content = content
            )
            DialogStyle.Material -> Surface(
                modifier = surfaceModifier,
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { Column(content = content) }
            DialogStyle.Radiant -> Column(
                modifier = surfaceModifier.appLiquidGlassSurface(
                    shape = AppComponentTokens.DialogShape,
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    level = com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel.Floating,
                    backdropSamplingEnabled = false
                ),
                content = content
            )
        }
    }
}

@Composable
private fun RadiantDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier,
    subtitle: String?,
    titleStyle: TextStyle,
    titleFontWeight: FontWeight?,
    titleMaxLines: Int,
    contentScrollable: Boolean,
    contentSpacing: Dp,
    properties: DialogProperties,
    actions: List<AppDialogAction>,
    headerContent: (@Composable ColumnScope.() -> Unit)?,
    content: (@Composable ColumnScope.() -> Unit)?
) {
    Dialog(onDismissRequest = onDismiss, properties = properties) {
        Column(
            modifier = modifier
                .clip(SmoothRoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = titleStyle,
                    fontWeight = titleFontWeight,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                headerContent?.invoke(this)
            }

            AppDialogDivider()

            if (content != null) {
                Column(
                    modifier = (if (contentScrollable) Modifier.weight(1f, fill = false) else Modifier)
                        .padding(24.dp)
                        .then(
                            if (contentScrollable) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        ),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                    content = content
                )
            }

            if (actions.isNotEmpty()) {
                AppDialogDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    actions.forEach { AppDialogCapsuleButton(it) }
                }
            }
        }
    }
}

@Composable
private fun AppDialogDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun RowScope.AppDialogCapsuleButton(action: AppDialogAction) {
    val scheme = MaterialTheme.colorScheme
    val (container, labelColor) = when (action.style) {
        AppDialogActionStyle.Neutral -> scheme.onSurface.copy(alpha = 0.08f) to scheme.onSurface
        AppDialogActionStyle.Primary -> scheme.primary.copy(alpha = 0.15f) to scheme.primary
        AppDialogActionStyle.Danger -> scheme.error.copy(alpha = 0.15f) to scheme.error
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (action.enabled) container else scheme.onSurface.copy(alpha = 0.05f))
            .clickable(enabled = action.enabled, onClick = action.onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = action.label,
            fontSize = 13.sp,
            color = if (action.enabled) labelColor else scheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium
        )
    }
}
