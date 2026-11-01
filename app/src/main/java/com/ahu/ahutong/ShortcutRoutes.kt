package com.ahu.ahutong

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 启动器静态快捷方式 → Compose 路由的唯一通道。
 * MainActivity 写入（冷启动 / onNewIntent），Main 在登录态就绪后消费。
 */
object ShortcutRoutes {
    const val EXTRA_ROUTE = "com.ahu.ahutong.extra.SHORTCUT_ROUTE"

    private val allowed = setOf("camera", "schedule", "campus_card", "electricity_pay")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun publish(route: String?) {
        if (route in allowed) _pending.value = route
    }

    fun consume(route: String) {
        if (_pending.value == route) _pending.value = null
    }
}
