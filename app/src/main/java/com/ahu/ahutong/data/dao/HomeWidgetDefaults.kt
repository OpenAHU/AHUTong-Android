package com.ahu.ahutong.data.dao

/** Defaults only; a saved per-account slot layout always takes precedence. */
internal object HomeWidgetDefaults {
    const val NOTICE_WIDGET_ID = "campus_notices"

    /** 首页固定位：stable-prep 恢复为校园通知；必吃榜留在 Best-Bites。 */
    const val PINNED_WIDGET_ID = NOTICE_WIDGET_ID

    /** 仅用于清理 Best-Bites 时期遗留的主页插槽。 */
    const val LEGACY_CANTEEN_WIDGET_ID = "canteen_ranking"

    val classic: List<String?> = listOf(
        "bathroom", "electricity", PINNED_WIDGET_ID
    ) + List(5) { null }

    val radiant: List<String?> = listOf(
        "electricity", "bathroom", "grade", "exam",
        "weather", "network_recharge", PINNED_WIDGET_ID
    )
}
