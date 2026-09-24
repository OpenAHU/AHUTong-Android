package com.ahu.ahutong.appwidget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun widgetScheduleFetchedText(fetchedAt: Long?): String =
    fetchedAt?.let {
        "获取于 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it))}"
    } ?: "尚未获取最新课表"
