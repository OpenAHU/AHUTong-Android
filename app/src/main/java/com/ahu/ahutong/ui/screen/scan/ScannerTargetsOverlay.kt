package com.ahu.ahutong.ui.screen.scan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Every marker opens only the payload whose bounds were tapped. */
@Composable
internal fun ScannerTargetsOverlay(
    candidates: List<QrDetection>,
    onSelect: (QrDetection) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        candidates.forEachIndexed { index, candidate ->
            key(candidate.raw, index) {
                val points = candidate.corners.map { projectQrPoint(it, candidate.aspectRatio, width, height) }
                val center = projectQrPoint(candidate.center, candidate.aspectRatio, width, height)
                val minX = points.minOfOrNull { it.x } ?: center.x
                val maxX = points.maxOfOrNull { it.x } ?: center.x
                val minY = points.minOfOrNull { it.y } ?: center.y
                val maxY = points.maxOfOrNull { it.y } ?: center.y
                val minimum = with(density) { 48.dp.toPx() }
                val targetWidth = maxOf(minimum, maxX - minX)
                val targetHeight = maxOf(minimum, maxY - minY)
                val left by animateFloatAsState((minX + maxX - targetWidth) / 2, spring(stiffness = 700f), label = "qr-choice-x")
                val top by animateFloatAsState((minY + maxY - targetHeight) / 2, spring(stiffness = 700f), label = "qr-choice-y")
                Box(
                    Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                        .size(with(density) { targetWidth.toDp() }, with(density) { targetHeight.toDp() })
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "选择二维码") { onSelect(candidate) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.QrCodeScanner, "选择二维码 ${index + 1}", tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
