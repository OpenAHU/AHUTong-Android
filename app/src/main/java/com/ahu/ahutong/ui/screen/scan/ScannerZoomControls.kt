package com.ahu.ahutong.ui.screen.scan

import androidx.compose.foundation.background
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.ahu.ahutong.ui.screen.camera.LocalCameraRotation
import java.util.Locale

internal data class ScannerZoomState(
    val ratio: Float = 1f,
    val minRatio: Float = 1f,
    val maxRatio: Float = 1f
) {
    private val validRange: Boolean get() = minRatio.isFinite() && maxRatio.isFinite() &&
        minRatio > 0f && maxRatio >= minRatio
    val isSupported: Boolean get() = validRange && maxRatio - minRatio > 0.01f
    val presets: List<Float> get() {
        if (!isSupported) return emptyList()
        return listOf(0.5f, 1f, 2f).filter { it in minRatio..maxRatio }
            .ifEmpty { listOf(minRatio, maxRatio).distinct() }
    }

    fun clamp(requested: Float): Float {
        if (!validRange) return 1f
        val fallback = ratio.takeIf { it.isFinite() } ?: 1f
        return requested.takeIf { it.isFinite() }?.coerceIn(minRatio, maxRatio)
            ?: fallback.coerceIn(minRatio, maxRatio)
    }

    fun scaledBy(factor: Float): Float {
        if (!validRange || !factor.isFinite() || factor <= 0f) return clamp(ratio)
        return (clamp(ratio).toDouble() * factor).coerceIn(minRatio.toDouble(), maxRatio.toDouble()).toFloat()
    }
}

internal fun scannerZoomLabel(ratio: Float): String =
    String.format(Locale.ROOT, "%.1f", ratio).removeSuffix(".0") + "×"

@Composable
internal fun ScannerZoomControls(
    state: ScannerZoomState,
    enabled: Boolean = true,
    onZoomRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isSupported) return
    val ratio = state.clamp(state.ratio)
    val presets = state.presets
    val elementRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = LocalCameraRotation.current,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 280,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "zoom-chip-rotation"
    )
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // 非预设倍率不再旁置文字：落在哪一档的区间内，就由那一档的圆显示实时倍率
        val activeIndex = presets.indexOfLast { it <= ratio + 0.001f }.coerceAtLeast(0)
        presets.forEachIndexed { index, preset ->
            val isSelected = index == activeIndex
            Box(Modifier
                .graphicsLayer { rotationZ = elementRotation }
                .clip(CircleShape)
                // 选中底用不透明灰（60% 白叠黑），压在亮画面上也够亮
                .background(if (isSelected) Color(0xFF999999) else Color.Transparent)
                .semantics { selected = isSelected }
                .clickable(enabled = enabled, role = Role.Button) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onZoomRatioChange(state.clamp(preset))
                }
                .size(36.dp), contentAlignment = Alignment.Center) {
                Text(scannerZoomLabel(if (isSelected) ratio else preset), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, softWrap = false,
                    color = (if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
                        .copy(alpha = if (enabled) 1f else 0.45f))
            }
        }
    }
}
