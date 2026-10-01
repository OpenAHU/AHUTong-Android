package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.ui.graphics.Color
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.HomeWidgetDefaults
import com.ahu.ahutong.data.model.AcademicFeatureAccess

/** 全部小工具页的分类（分组展示用）。归属调整只改各条目的标注，布局代码不感知。 */
enum class WidgetCategory(val title: String) {
    PAYMENT("缴费充值"),
    ACADEMIC("学业教务"),
    CAMPUS("校园生活"),
    LEARNING("学习工具"),
    PLUGIN("插件")
}

data class HomeWidgetSpec(
    val id: String,
    val title: String,
    val route: String,
    val iconId: Int,
    val tint: Color,
    /** 运行期插件的 PNG 图标字节流（非空时优先于 iconId 渲染）。 */
    val iconBytes: ByteArray? = null,
    /** 全部小工具页分组；运行期插件走默认值 PLUGIN，插件构造零感知（.ahup 兼容不破坏）。 */
    val category: WidgetCategory = WidgetCategory.PLUGIN
)

object HomeWidgetRegistry {

    /** 插件入口（ServiceLoader 发现，宿主对插件零静态引用）。 */
    val pluginWidgets: List<HomeWidgetSpec>
        get() = com.ahu.ahutong.ui.plugin.PluginRegistry.plugins.value.map { plugin ->
            val resIcon = (plugin.meta.icon as? com.ahu.ahutong.core.plugin.PluginIcon.Resource)?.resId
            val bytesIcon = (plugin.meta.icon as? com.ahu.ahutong.core.plugin.PluginIcon.Bytes)?.png
            HomeWidgetSpec(
                id = "plugin:" + plugin.meta.id,
                title = plugin.meta.title,
                route = "plugin/" + plugin.meta.id,
                iconId = resIcon ?: 0,
                tint = Color(plugin.meta.tint),
                iconBytes = bytesIcon
            )
        }

    /** 经典版（Original/Liquid Glass）主页插槽数量：双列布局（校园卡旁 2 个 + 三行各 2 个）。 */
    const val slotCountClassic = 8
    const val slotCount = slotCountClassic

    /** 曜光版（RadiantUI）主页插槽数量：图标网格（4 + 3，末位为「更多」入口）。 */
    const val slotCountRadiant = 7

    /** Radiant 配置直接读取时的兜底；主页首次加载仍沿用 Classic 默认槽位。 */
    val defaultSlotsRadiant: List<String?> = HomeWidgetDefaults.radiant

    /** 内置小工具 + 插件入口（插件由 ServiceLoader 发现，可为空）。 */
    val widgets: List<HomeWidgetSpec> get() = builtInWidgets + pluginWidgets

    private val builtInWidgets = listOf(
        // —— 缴费充值 ——
        HomeWidgetSpec(
            id = "bathroom",
            title = "浴室缴费",
            route = "bathroom_deposit",
            iconId = R.drawable.ic_bathroom_pay,
            tint = Color(0xFF26A69A),
            category = WidgetCategory.PAYMENT
        ),
        HomeWidgetSpec(
            id = "electricity",
            title = "电控缴费",
            route = "electricity_pay",
            iconId = R.drawable.ic_electricity_pay,
            tint = Color(0xFFFFB300),
            category = WidgetCategory.PAYMENT
        ),
        HomeWidgetSpec(
            id = "network_recharge",
            title = "网费充值",
            route = "network_recharge",
            iconId = R.drawable.ic_network_recharge,
            tint = Color(0xFF1E88E5),
            category = WidgetCategory.PAYMENT
        ),
        // —— 学业教务 ——
        HomeWidgetSpec(
            id = "grade",
            title = "成绩单",
            route = "grade",
            iconId = R.drawable.ic_grade,
            tint = Color(0xFFFFC107),
            category = WidgetCategory.ACADEMIC
        ),
        HomeWidgetSpec(
            id = "exam",
            title = "考场查询",
            route = "exam",
            iconId = R.drawable.ic_exam,
            tint = Color(0xFF4CAF50),
            category = WidgetCategory.ACADEMIC
        ),
        HomeWidgetSpec(
            id = "evaluation",
            title = "教评",
            route = "evaluation",
            iconId = R.drawable.ic_evaluation,
            tint = Color(0xFF0D9488),
            category = WidgetCategory.ACADEMIC
        ),
        HomeWidgetSpec(
            id = "school_calendar",
            title = "校历",
            route = "school_calendar",
            iconId = R.drawable.ic_schedule,
            tint = Color(0xFF9C27B0),
            category = WidgetCategory.ACADEMIC
        ),
        HomeWidgetSpec(
            id = "program_completion",
            title = "培养方案",
            route = "program_completion",
            iconId = R.drawable.ic_nav_degree_hat,
            tint = Color(0xFF009688),
            category = WidgetCategory.ACADEMIC
        ),
        HomeWidgetSpec(
            id = "free_classroom",
            title = "空闲教室",
            route = "free_classroom",
            iconId = R.drawable.ic_round_business_24,
            tint = Color(0xFF03A9F4),
            category = WidgetCategory.ACADEMIC
        ),
        // —— 校园生活 ——
        HomeWidgetSpec(
            id = "lost_found",
            title = "失物招领",
            route = "lost_found",
            iconId = R.drawable.lost_and_found,
            tint = Color(0xFF1976D2),
            category = WidgetCategory.CAMPUS
        ),
        HomeWidgetSpec(
            id = "weather",
            title = "天气",
            route = "weather",
            iconId = R.drawable.ic_weather,
            tint = Color(0xFFFFB300),
            category = WidgetCategory.CAMPUS
        ),
        HomeWidgetSpec(
            id = "campus_notices",
            title = "校园通知",
            route = "campus_notices",
            iconId = R.drawable.ic_campus_notice,
            tint = Color(0xFF1976D2),
            category = WidgetCategory.CAMPUS
        ),
        HomeWidgetSpec(
            id = "student_mail",
            title = "学生邮箱",
            route = "student_mail",
            iconId = R.drawable.ic_student_mail,
            tint = Color(0xFF3978D4),
            category = WidgetCategory.CAMPUS
        ),
        HomeWidgetSpec(
            id = "phone_book",
            title = "电话本",
            route = "phone_book",
            iconId = R.drawable.ic_phonebook,
            tint = Color(0xFF009688),
            category = WidgetCategory.CAMPUS
        ),
        HomeWidgetSpec(
            id = "identity_code",
            title = "身份码",
            route = "identity_code",
            iconId = R.drawable.ic_identity_code,
            tint = Color(0xFFFF5000), // 淘宝橙
            category = WidgetCategory.CAMPUS
        ),
        // —— 学习工具 ——
        HomeWidgetSpec(
            id = "repository",
            title = "学习资料",
            route = "repository",
            iconId = R.drawable.ic_repository,
            tint = Color(0xFF8D6E63),
            category = WidgetCategory.LEARNING
        ),
        HomeWidgetSpec(
            id = "xuexiaotong",
            title = "学习通日历",
            route = "xuexiaotong",
            iconId = R.drawable.ic_xuexiaotong,
            tint = Color(0xFF7C4DFF),
            category = WidgetCategory.LEARNING
        )
    )

    val widgetById = widgets.associateBy { it.id }

    /**
     * 当前风格下可展示的小工具列表。
     * 曜光版下「学习通日历」已提级为底部 tab，从小工具列表 / 主页插槽中隐藏。
     */
    fun availableWidgets(
        radiant: Boolean,
        undergraduateEnabled: Boolean = AHUCache.canUseUndergraduateAcademics()
    ): List<HomeWidgetSpec> = widgets.filter {
        (!radiant || it.id != "xuexiaotong") &&
            (undergraduateEnabled || it.route !in AcademicFeatureAccess.postgraduateHiddenRoutes)
    }
}
