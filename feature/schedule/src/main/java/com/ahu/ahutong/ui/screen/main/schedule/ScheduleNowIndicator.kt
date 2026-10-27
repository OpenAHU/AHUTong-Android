package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.monet.a1
import com.kyant.monet.withNight

/**
 * 课表「当前时间」指示线：位置换算 + 绘制。
 *
 * 位置换算是纯函数（见 ScheduleNowIndicatorTest）：
 * - 全部课程结束后 → null（不画线）
 * - 首节课开始前 → [NowIndicatorPosition.InGap]（afterSection = 0），
 *   fraction 为「当天 0 点 → 首节课开始」的进度，绘制时映射到内容网格顶部与第一行之间，
 *   线悬在第一节上方，随时间缓慢下探
 * - 某节课进行中 → [NowIndicatorPosition.InSection]，fraction 为该节起止分钟内的进度，
 *   绘制时映射到该节的整行高度（合并成一张卡的多节课，每节各自映射，线连续前进）
 * - 两节课之间的间隙（课间、午休、空白节）→ [NowIndicatorPosition.InGap]，
 *   fraction 为「上节结束 → 下节开始」的进度，绘制时映射到两行之间的 cellSpacing
 */
sealed interface NowIndicatorPosition {
    data class InSection(val section: Int, val fraction: Float) : NowIndicatorPosition
    data class InGap(val afterSection: Int, val fraction: Float) : NowIndicatorPosition
}

fun nowIndicatorPosition(currentMinutes: Int, timetable: Map<Int, String>): NowIndicatorPosition? {
    data class Anchor(val section: Int, val start: Int, val end: Int)

    val anchors = timetable.entries
        .mapNotNull { (section, clock) ->
            val parts = clock.split('-')
            if (parts.size != 2) return@mapNotNull null
            val start = parseClockMinutesOrNull(parts[0]) ?: return@mapNotNull null
            val end = parseClockMinutesOrNull(parts[1]) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            Anchor(section, start, end)
        }
        .sortedBy { it.start }

    if (anchors.isEmpty()) return null
    if (currentMinutes >= anchors.last().end) return null

    // 首节课开始前：悬在第一节上方，从内容网格顶起随时间下探
    if (currentMinutes < anchors.first().start) {
        val firstStart = anchors.first().start
        val fraction = if (firstStart > 0) currentMinutes.toFloat() / firstStart else 0f
        return NowIndicatorPosition.InGap(
            afterSection = 0,
            fraction = fraction.coerceIn(0f, 1f)
        )
    }

    anchors.forEachIndexed { index, anchor ->
        if (currentMinutes < anchor.end) {
            return NowIndicatorPosition.InSection(
                section = anchor.section,
                fraction = (currentMinutes - anchor.start).toFloat() / (anchor.end - anchor.start)
            )
        }
        val next = anchors.getOrNull(index + 1) ?: return null
        if (currentMinutes < next.start) {
            val gap = next.start - anchor.end
            val fraction = if (gap > 0) {
                (currentMinutes - anchor.end).toFloat() / gap
            } else {
                0f
            }
            return NowIndicatorPosition.InGap(
                afterSection = anchor.section,
                fraction = fraction.coerceIn(0f, 1f)
            )
        }
    }
    return null
}

private fun parseClockMinutesOrNull(clock: String): Int? {
    val separator = clock.indexOf(':')
    if (separator <= 0 || separator >= clock.lastIndex) return null
    val hour = clock.substring(0, separator).toIntOrNull() ?: return null
    val minute = clock.substring(separator + 1).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun formatNowClock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * 当前时间横线，横穿内容网格（不含表头行与节次列），叠在课程卡片之上。
 */
@Composable
fun BoxScope.ScheduleNowIndicator(
    position: NowIndicatorPosition,
    nowMinutes: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    dayCount: Int
) {
    // 跟随软件主题动态主色（与列头"今天"胶囊、周选择器同源 a1 令牌），
    // 深浅色各自适配：浅色 40.a1（与选中周胶囊一致），深色提亮 80.a1 保证在玻璃卡上可读
    val lineColor = 40.a1 withNight 80.a1
    val clock = formatNowClock(nowMinutes)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "当前时间 $clock" }
    ) {
        val cellWidthPx = cellWidth.toPx()
        val cellHeightPx = cellHeight.toPx()
        val mainColumnWidthPx = CourseCardSpec.mainColumnWidth.toPx()
        val mainRowHeightPx = CourseCardSpec.mainRowHeight.toPx()
        val spacingPx = CourseCardSpec.cellSpacing.toPx()

        val y = when (val p = position) {
            is NowIndicatorPosition.InSection ->
                mainRowHeightPx +
                    (cellHeightPx + spacingPx) * (p.section - 1) +
                    spacingPx +
                    p.fraction * cellHeightPx
            is NowIndicatorPosition.InGap -> {
                // 课前：悬在内容网格顶（表头行下缘）与第一行顶之间
                if (p.afterSection == 0) {
                    mainRowHeightPx + p.fraction * spacingPx
                } else {
                    mainRowHeightPx +
                        (cellHeightPx + spacingPx) * (p.afterSection - 1) +
                        spacingPx +
                        cellHeightPx +
                        p.fraction * spacingPx
                }
            }
        }
        val left = mainColumnWidthPx
        val right = mainColumnWidthPx + dayCount * (cellWidthPx + spacingPx)

        // 视觉规格：透明度 50%；磅数 = 2dp 的 80% ≈ 1.6dp，按当前密度取整数 px
        val strokeWidth = (2.dp.toPx() * 0.8f + 0.5f).toInt().toFloat()
        drawLine(
            color = lineColor.copy(alpha = 0.5f),
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
