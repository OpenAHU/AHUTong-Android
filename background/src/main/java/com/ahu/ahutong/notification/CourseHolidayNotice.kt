package com.ahu.ahutong.notification

internal fun withCourseHolidayNotice(content: String, holidayNotice: String?): String =
    holidayNotice?.takeIf { it.isNotBlank() }?.let { "$content\n$it" } ?: content
