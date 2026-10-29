package com.ahu.ahutong.ext

import com.ahu.ahutong.core.common.UserNoticeHolder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 协程的兜底异常处理：只负责记录与**上报**，不决定用户看到什么。
 * 提示实现由 `:app` 安装（见 [UserNoticeHolder]），因此核心模块不再依赖 Toast 与文案。
 */
val GlobalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    UserNoticeHolder.reportUnhandled(throwable)
}

fun CoroutineScope.launchSafe(
    block: suspend CoroutineScope.() -> Unit
) = launch(GlobalCoroutineExceptionHandler) {
    block()
}
