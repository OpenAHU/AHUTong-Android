package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel

/**
 * 状态卡族：加载 / 空态 / 错误三态的统一实现（P0 组件）。
 *
 * 全部构建在契约组件（AppCard / AppButton / AppCircularProgressIndicator）之上，
 * 内部零主题分支。居中三态默认撑满可用宽度并在其中居中，需要整页居中时
 * 调用方传 `Modifier.fillMaxSize()`；[Inline] 是卡片内联形态。
 *
 * 蓝本：FreeClassroom.MessageCard、SchoolCalendar 的 CalendarLoadingState /
 * CalendarEmptyState（收敛全局 6+ 份同构状态视图）。
 */
object AppStateCard {

    /** 加载中：进度圈 + 可选文案。 */
    @Composable
    fun Loading(
        message: String? = null,
        modifier: Modifier = Modifier
    ) {
        StateFrame(modifier) {
            AppCircularProgressIndicator()
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    /** 空态：可选图标 + 主文案（+ 副文案）+ 可选动作按钮。 */
    @Composable
    fun Empty(
        message: String,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        subtitle: String? = null,
        actionLabel: String? = null,
        actionIcon: ImageVector? = null,
        onAction: (() -> Unit)? = null
    ) {
        StateFrame(modifier) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                AppButton(onClick = onAction, variant = AppButtonVariant.Secondary) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(actionLabel)
                }
            }
        }
    }

    /** 错误：标题 + 详情 + 重试按钮（默认带刷新图标）；title 传 null 时不显示标题行。 */
    @Composable
    fun Error(
        message: String,
        modifier: Modifier = Modifier,
        title: String? = "加载失败",
        retryLabel: String = "重试",
        onRetry: (() -> Unit)? = null
    ) {
        StateFrame(modifier) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                AppButton(onClick = onRetry, variant = AppButtonVariant.Secondary) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(retryLabel)
                }
            }
        }
    }

    /**
     * 内联卡片形态：主题原生卡片容器内左对齐排布，各区块自由组合。
     * 蓝本：FreeClassroom.MessageCard、NetworkRecharge.LoadingCard/ErrorCard。
     */
    @Composable
    fun Inline(
        modifier: Modifier = Modifier,
        // 懒列表项需自带 16dp 边距；放在 AppPageScaffold content（壳已提供水平边距）中时传 0.dp
        horizontalInset: Dp = 16.dp,
        loading: Boolean = false,
        title: String? = null,
        message: String? = null,
        actionLabel: String? = null,
        actionIcon: ImageVector? = null,
        onAction: (() -> Unit)? = null
    ) {
        AppCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalInset),
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppCircularProgressIndicator()
                    }
                }
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if (message != null) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (actionLabel != null && onAction != null) {
                    AppButton(onClick = onAction, variant = AppButtonVariant.Secondary) {
                        if (actionIcon != null) {
                            Icon(actionIcon, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(actionLabel)
                    }
                }
            }
        }
    }

    /**
     * 内联错误卡片：errorContainer 语义染色容器 + 标题/详情/全宽重试。
     * 蓝本：ElectricityDeposit / LostFound / Evaluation 三处手写错误卡（容器逻辑原样收编）。
     */
    @Composable
    fun InlineError(
        message: String,
        modifier: Modifier = Modifier,
        // 同 [Inline]：content 页面传 0.dp，避免与壳的水平边距叠加
        horizontalInset: Dp = 16.dp,
        title: String? = "加载失败",
        retryLabel: String = "重试",
        onRetry: (() -> Unit)? = null
    ) {
        val content: @Composable ColumnScope.() -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (onRetry != null) {
                    AppButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        variant = AppButtonVariant.Secondary
                    ) {
                        Text(retryLabel)
                    }
                }
            }
        }
        if (isRadiantUi) {
            GlassCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                overlayColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalInset)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
        } else {
            Column(
                modifier = modifier
                    .padding(horizontal = horizontalInset)
                    .fillMaxWidth()
                    .appLiquidGlassSurface(
                        shape = AppComponentTokens.CardShape,
                        fallbackColor = MaterialTheme.colorScheme.errorContainer,
                        level = LiquidGlassSurfaceLevel.Panel
                    )
                    .padding(16.dp),
                content = content
            )
        }
    }

    @Composable
    private fun StateFrame(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}
