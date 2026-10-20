package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.components.isRadiantUi
import com.kyant.monet.Hct.Companion.toHct
import com.kyant.monet.a1
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb

/**
 * 课表课程配色方案（主题感知组件）：
 * 曜光 = 马卡龙 11 色板（按课名次序取用，溢出回退 hash）；其余 = 主题基色 HCT 色环均分。
 * 各主题保留自己的调色盘实现，这里只是把它们收敛为一个取值入口。
 *
 * [customColors] 是用户在自定义课程里亲手选的颜色（按课名索引），优先于自动配色。
 */
@Composable
fun rememberCourseColors(
    schedule: List<Course>,
    customColors: Map<String, Color> = emptyMap()
): Map<String, Color> {
    val radiant = isRadiantUi
    val baseColor = 50.a1.toSrgb().toHct()
    val macaronPalette = remember {
        listOf(
            Color(0xFF82ADF7), Color(0xFF7AE3D2), Color(0xFF77B6EF),
            Color(0xFFE19BB0), Color(0xFFE38874), Color(0xFF679ACD),
            Color(0xFFE87897), Color(0xFFEBB877), Color(0xFFC8A2C8),
            Color(0xFFA8E4A0), Color(0xFFFF8A80)
        )
    }
    return remember(schedule, radiant, customColors) {
        val courseNames = schedule.asSequence().map { it.name }.distinct().toList()
        val generated = if (radiant) {
            courseNames.mapIndexed { index, name ->
                val paletteIndex = if (index < macaronPalette.size) {
                    index
                } else {
                    (name?.hashCode() ?: 0).mod(macaronPalette.size)
                }
                name to macaronPalette[paletteIndex]
            }.toMap()
        } else {
            courseNames.mapIndexed { index, name ->
                name to baseColor.copy(
                    h = 360.0 * index / courseNames.size.coerceAtLeast(1)
                ).toSrgb().toColor()
            }.toMap()
        }
        generated + customColors
    }
}
