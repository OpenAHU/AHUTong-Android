package com.ahu.ahutong.data.model

enum class AcademicAccountType { UNDERGRADUATE, POSTGRADUATE }

/** One policy shared by navigation, home shortcuts and background academic work. */
object AcademicFeatureAccess {
    val undergraduateRoutes = setOf("schedule", "grade", "exam", "evaluation", "free_classroom", "info")

    fun allowsRoute(type: AcademicAccountType?, route: String?): Boolean =
        type != AcademicAccountType.POSTGRADUATE ||
            route?.substringBefore('?') !in undergraduateRoutes
}
