package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.n2
import com.kyant.monet.withNight

@Composable
fun CourseCard(
    course: Course,
    color: Color,
    cellWidth: Dp,
    cellHeight: Dp,
    isCurrentWeek: Boolean = true,
    onClick: (Course) -> Unit
) {
    CompositionLocalProvider(
        LocalTonalPalettes provides color.toTonalPalettes(
            style = PaletteStyle.Vibrant, tonalValues = doubleArrayOf() // 此行代码解决了卡顿问题
        )
    ) {
        // Radiant：课程名省略行数按「卡高 - 地点胶囊实际高度 - 自身边距」实时推算
        val nameMaxLines = if (isRadiantUi) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val capsuleText =
                if (isCurrentWeek) course.location.shortScheduleLocation() else "非本周"
            remember(course.name, course.length, capsuleText, cellWidth, cellHeight) {
                val capsuleStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                val nameStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val capsuleTextLayout = textMeasurer.measure(
                    capsuleText,
                    capsuleStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    constraints = Constraints(
                        maxWidth = with(density) { (cellWidth - 12.dp).roundToPx() }
                    )
                )
                val nameLineHeight = textMeasurer.measure("口", nameStyle)
                    .size.height.coerceAtLeast(1)
                val cardHeightPx = with(density) {
                    (cellHeight * course.length +
                            CourseCardSpec.cellSpacing * (course.length - 1)).roundToPx()
                }
                val capsuleTotalPx = capsuleTextLayout.size.height +
                        with(density) { 12.dp.roundToPx() }
                val nameAvailablePx = cardHeightPx - capsuleTotalPx -
                        with(density) { 8.dp.roundToPx() }
                (nameAvailablePx / nameLineHeight).coerceIn(1, 8)
            }
        } else {
            3
        }
        Box(
            modifier = with(CourseCardSpec) {
                Modifier
                    .size(
                        cellWidth, cellHeight * course.length + cellSpacing * (course.length - 1)
                    )
                    .offset(
                        mainColumnWidth + (cellWidth + cellSpacing) * (course.weekday - 1) + cellSpacing,
                        mainRowHeight + (cellHeight + cellSpacing) * (course.startTime - 1) + cellSpacing
                    )
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .background(if (!isCurrentWeek) Color.Gray else color)
                    .pointerInput(Unit) {
                        detectTapGestures { onClick(course) }
                    }
            }) {
            Text(
                text = course.name,
                modifier = Modifier.padding(4.dp),
                color = 100.n1,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = nameMaxLines,
                style = if (isRadiantUi) {
                    TextStyle(fontSize = 12.sp)
                } else {
                    MaterialTheme.typography.labelMedium
                }
            )


            Text(
                // TODO: more shortenings
                text = if (isCurrentWeek) {
                    course.location.shortScheduleLocation()
                } else {
                    "非本周"
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(4.dp)
                    .clip(SmoothRoundedCornerShape(6.dp))
                    .background(
                        if (!isCurrentWeek) Color.Gray.copy(
                            0.7f, 0.7f, 0.7f, 0.7f
                        ) withNight Color.Gray.copy(0.7f, 0.3f, 0.3f, 0.3f)
                        else 95.a1 withNight 30.n2
                    )
                    .padding(2.dp),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = TextStyle(
                    fontSize = if (isRadiantUi) 9.sp else 11.sp,
                    color = 10.n1 withNight 90.n1,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
