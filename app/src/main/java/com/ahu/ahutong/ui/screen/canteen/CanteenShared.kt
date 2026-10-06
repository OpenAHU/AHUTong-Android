package com.ahu.ahutong.ui.screen.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 必吃榜内容页的共享视觉语言（素底点评风）。
 * 足迹页与全校榜共用：锁亮色、橙色点睛、彩色面积克制。
 */
internal object CanteenPalette {
    val accent = Color(0xFFFF6633)       // 点评橙：点睛色
    val accentDeep = Color(0xFFD64A17)   // 热力最高档
    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF999999)
    val cardLine = Color(0xFFE8E6E0)
    val heat0 = Color(0xFFF0EFE9)
    val heat1 = Color(0xFFFFD9C2)
    val heat2 = Color(0xFFFFAF85)
    val championBg = Color(0xFFFFF9F5)
    val gold = Color(0xFFF5A623)
    val bronze = Color(0xFFC08A5A)
}

internal val HeroNumber = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Medium)
internal val SectionTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
internal val BodyText = TextStyle(fontSize = 13.sp)
internal val CaptionText = TextStyle(fontSize = 11.sp)

/** 橙色竖条小节头。 */
@Composable
internal fun SectionHeader(title: String, tail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .background(CanteenPalette.accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(title, style = SectionTitle, color = CanteenPalette.textPrimary)
        }
        if (tail != null) {
            Text(tail, style = CaptionText, color = CanteenPalette.accent)
        }
    }
}

/** 描边徽章 chip（本周尝新 ×2 这类）。 */
@Composable
internal fun OutlineChip(text: String) {
    Box(
        Modifier
            .border(0.5.dp, CanteenPalette.cardLine, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = CaptionText, color = Color(0xFF666666))
    }
}

/** 分 → "¥12.30"。 */
internal fun Long.money(): String = "¥%.2f".format(this / 100.0).trimEnd('0').trimEnd('.')
