package com.ahu.ahutong.ui.glow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug 用的光效开关：右下角一颗小圆钮，点一下开关屏幕边缘光效。
 * 只用于评估视觉效果，不接任何业务状态。
 */
@Composable
fun GlowTestOverlay() {
    var enabled by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (enabled) {
            ScreenEdgeGlow()
        }
        Surface(
            onClick = { enabled = !enabled },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 112.dp)
                .size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = if (enabled) "关" else "光", fontSize = 12.sp)
            }
        }
    }
}
