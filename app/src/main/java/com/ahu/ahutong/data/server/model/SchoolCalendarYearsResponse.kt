package com.ahu.ahutong.data.server.model

data class SchoolCalendarYearsResponse(
    val years: List<String> = emptyList(),
    val latestYear: String? = null
)
