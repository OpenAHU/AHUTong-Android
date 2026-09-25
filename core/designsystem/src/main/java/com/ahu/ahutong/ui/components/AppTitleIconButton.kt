package com.ahu.ahutong.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.core.designsystem.R
import com.ahu.ahutong.core.designsystem.RefreshState

/**
 * 标题栏圆形图标按钮（P1 组件）：34dp 圆 + onSurface 6% 底色 + 18dp 图标。
 *
 * 收敛自 Grade/Exam/LostFound/PhoneBook/Evaluation/Repository 的 7 份同构拷贝。
 * [icon]（drawableRes）与 [imageVector] 二选一；[loading] 时以进度圈替换图标并禁用点击；
 * 需要完全自定义内容时传 [content] 槽。
 */
@Composable
fun AppTitleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    @DrawableRes icon: Int? = null,
    imageVector: ImageVector? = null,
    tint: Color? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, enabled = enabled && !loading) {
            when {
                loading -> AppCircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                content != null -> content()
                imageVector != null -> Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = tint ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                icon != null -> Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = tint ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 三态刷新按钮（P1 组件）：IDLE=刷新图标 / LOADING=进度圈（禁用）/ UPDATED=绿色对勾。
 * 收敛自 Exam 的 ExamRadiantRefreshButton 与 Repository 的 loading 变体。
 * 行为上报（BehaviorActionReporter）由各页面在 onClick 内完成，不进组件。
 */
@Composable
fun AppRefreshButton(
    state: RefreshState,
    onClick: () -> Unit,
    contentDescription: String = "刷新",
    modifier: Modifier = Modifier
) {
    AppTitleIconButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        loading = state == RefreshState.LOADING
    ) {
        when (state) {
            RefreshState.LOADING -> Unit // loading 分支已在上方渲染进度圈
            RefreshState.UPDATED -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已更新",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(18.dp)
            )
            RefreshState.IDLE -> Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
