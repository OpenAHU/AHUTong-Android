package com.ahu.ahutong.ui.screen.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (presets.none { abs(it - ratio) < 0.025f }) {
            Text(scannerZoomLabel(ratio), color = Color.White,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
        }
        presets.forEach { preset ->
            val isSelected = abs(preset - ratio) < 0.025f
            Box(Modifier.clip(CircleShape)
                .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.6f))
                .semantics { selected = isSelected }
                .clickable(enabled = enabled, role = Role.Button) { onZoomRatioChange(state.clamp(preset)) }
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(scannerZoomLabel(preset), style = MaterialTheme.typography.labelLarge,
                    color = (if (isSelected) Color.Black else Color.White).copy(alpha = if (enabled) 1f else 0.45f))
            }
        }
    }
}
