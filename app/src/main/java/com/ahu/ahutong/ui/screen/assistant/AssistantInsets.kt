package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 键盘避让的“真实”高度：窗口已经因为 resize 缩掉的部分要扣掉，
 * 否则 ime inset 和窗口收缩会叠加成双倍高度。
 */
@Composable
fun rememberEffectiveImePadding(): Dp {
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density)
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val screenHeight = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.roundToPx()
    }
    val shrunk = (screenHeight - containerHeight).coerceAtLeast(0)
    val effective = if (imePx > 0 && shrunk >= imePx / 2) 0 else imePx
    return with(density) { effective.toDp() }
}
