package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.ui.graphics.Color
import com.ahu.ahutong.feature.home.R

data class HomeWidgetSpec(
    val id: String,
    val titleResId: Int,
    val route: String,
    val iconId: Int,
    val tint: Color
)

object HomeWidgetRegistry {
    const val slotCount = 8

    val widgets = listOf(
        HomeWidgetSpec(
            id = "bathroom",
            titleResId = R.string.bathroom_payment,
            route = "bathroom_deposit",
            iconId = R.drawable.ic_bathroom_pay,
            tint = Color(0xFF26A69A)
        ),
        HomeWidgetSpec(
            id = "electricity",
            titleResId = R.string.electricity_control_payment,
            route = "electricity_pay",
            iconId = R.drawable.ic_electricity_pay,
            tint = Color(0xFFFFB300)
        ),
        HomeWidgetSpec(
            id = "grade",
            titleResId = R.string.grade_report,
            route = "grade",
            iconId = R.drawable.ic_grade,
            tint = Color(0xFFFFC107)
        ),
        HomeWidgetSpec(
            id = "phone_book",
            titleResId = R.string.phone_book,
            route = "phone_book",
            iconId = R.drawable.ic_phonebook,
            tint = Color(0xFF009688)
        ),
        HomeWidgetSpec(
            id = "exam",
            titleResId = R.string.exam_query,
            route = "exam",
            iconId = R.drawable.ic_exam,
            tint = Color(0xFF4CAF50)
        ),
        HomeWidgetSpec(
            id = "school_calendar",
            titleResId = R.string.school_calendar,
            route = "school_calendar",
            iconId = R.drawable.ic_schedule,
            tint = Color(0xFF9C27B0)
        ),
        HomeWidgetSpec(
            id = "free_classroom",
            titleResId = R.string.free_classroom,
            route = "free_classroom",
            iconId = R.drawable.ic_round_business_24,
            tint = Color(0xFF03A9F4)
        ),
        HomeWidgetSpec(
            id = "lost_found",
            titleResId = R.string.lost_and_found,
            route = "lost_found",
            iconId = R.drawable.lost_and_found,
            tint = Color(0xFF1976D2)
        ),
        HomeWidgetSpec(
            id = "weather",
            titleResId = R.string.weather,
            route = "weather",
            iconId = R.drawable.ic_weather,
            tint = Color(0xFFFFB300)
        ),
        HomeWidgetSpec(
            id = "repository",
            titleResId = R.string.learning_materials,
            route = "repository",
            iconId = R.drawable.ic_repository,
            tint = Color(0xFF8D6E63)
        )
    )

    val widgetById = widgets.associateBy { it.id }
}
