package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.ui.graphics.Color
import com.ahu.ahutong.R

data class HomeWidgetSpec(
    val id: String,
    val title: String,
    val route: String,
    val iconId: Int,
    val tint: Color
)

object HomeWidgetRegistry {
    /** 经典版（Original/Liquid Glass）主页插槽数量：双列布局（校园卡旁 2 个 + 三行各 2 个）。 */
    const val slotCountClassic = 8

    /** 曜光版（RadiantUI）主页插槽数量：图标网格（4 + 3，末位为「更多」入口）。 */
    const val slotCountRadiant = 7

    /** RadiantUI 首次启动的默认插槽（7 个填满，按展示顺序）。 */
    val defaultSlotsRadiant: List<String?> = listOf(
        "electricity", "bathroom", "grade", "exam",
        "weather", "network_recharge", "free_classroom"
    )

    val widgets = listOf(
        HomeWidgetSpec(
            id = "bathroom",
            title = "浴室缴费",
            route = "bathroom_deposit",
            iconId = R.drawable.ic_bathroom_pay,
            tint = Color(0xFF26A69A)
        ),
        HomeWidgetSpec(
            id = "electricity",
            title = "电控缴费",
            route = "electricity_pay",
            iconId = R.drawable.ic_electricity_pay,
            tint = Color(0xFFFFB300)
        ),
        HomeWidgetSpec(
            id = "grade",
            title = "成绩单",
            route = "grade",
            iconId = R.drawable.ic_grade,
            tint = Color(0xFFFFC107)
        ),
        HomeWidgetSpec(
            id = "phone_book",
            title = "电话本",
            route = "phone_book",
            iconId = R.drawable.ic_phonebook,
            tint = Color(0xFF009688)
        ),
        HomeWidgetSpec(
            id = "exam",
            title = "考场查询",
            route = "exam",
            iconId = R.drawable.ic_exam,
            tint = Color(0xFF4CAF50)
        ),
        HomeWidgetSpec(
            id = "evaluation",
            title = "教评",
            route = "evaluation",
            iconId = R.drawable.ic_evaluation,
            tint = Color(0xFF0D9488)
        ),
        HomeWidgetSpec(
            id = "school_calendar",
            title = "校历",
            route = "school_calendar",
            iconId = R.drawable.ic_schedule,
            tint = Color(0xFF9C27B0)
        ),
        HomeWidgetSpec(
            id = "free_classroom",
            title = "空闲教室",
            route = "free_classroom",
            iconId = R.drawable.ic_round_business_24,
            tint = Color(0xFF03A9F4)
        ),
        HomeWidgetSpec(
            id = "lost_found",
            title = "失物招领",
            route = "lost_found",
            iconId = R.drawable.lost_and_found,
            tint = Color(0xFF1976D2)
        ),
        HomeWidgetSpec(
            id = "weather",
            title = "天气",
            route = "weather",
            iconId = R.drawable.ic_weather,
            tint = Color(0xFFFFB300)
        ),
        HomeWidgetSpec(
            id = "repository",
            title = "学习资料",
            route = "repository",
            iconId = R.drawable.ic_repository,
            tint = Color(0xFF8D6E63)
        ),
        HomeWidgetSpec(
            id = "xuexiaotong",
            title = "学习通日历",
            route = "xuexiaotong",
            iconId = R.drawable.ic_xuexiaotong,
            tint = Color(0xFF7C4DFF)
        ),
        HomeWidgetSpec(
            id = "network_recharge",
            title = "网费充值",
            route = "network_recharge",
            iconId = R.drawable.ic_network_recharge,
            tint = Color(0xFF1E88E5)
        )
    )

    val widgetById = widgets.associateBy { it.id }

    /**
     * 当前风格下可展示的小工具列表。
     * 曜光版下「学习通日历」已提级为底部 tab，从小工具列表 / 主页插槽中隐藏。
     */
    fun availableWidgets(radiant: Boolean): List<HomeWidgetSpec> =
        if (radiant) widgets.filter { it.id != "xuexiaotong" } else widgets
}