package com.ahu.ahutong.data.model

enum class AcademicAccountType { UNDERGRADUATE, POSTGRADUATE }

/** One policy shared by navigation, home shortcuts and background academic work. */
object AcademicFeatureAccess {
    // The schedule destination dispatches to a separate GMIS screen for graduates.
    val undergraduateRoutes = setOf("grade", "exam", "evaluation", "free_classroom", "info")
    val postgraduateHiddenRoutes = undergraduateRoutes + "xuexiaotong"

    fun allowsRoute(type: AcademicAccountType?, route: String?): Boolean =
        type != AcademicAccountType.POSTGRADUATE ||
            route?.substringBefore('?') !in postgraduateHiddenRoutes
}
