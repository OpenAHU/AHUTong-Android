package com.ahu.ahutong.data.dao

/** Defaults only; a saved per-account slot layout always takes precedence. */
internal object HomeWidgetDefaults {
    const val NOTICE_WIDGET_ID = "campus_notices"

    /** 首页固定位（原校园通知位，2026-10-07 替换）：必吃榜。 */
    const val PINNED_WIDGET_ID = "canteen_ranking"

    val classic: List<String?> = listOf(
        "bathroom", "electricity", PINNED_WIDGET_ID
    ) + List(5) { null }

    val radiant: List<String?> = listOf(
        "electricity", "bathroom", "grade", "exam",
        "weather", "network_recharge", PINNED_WIDGET_ID
    )
}
