package com.ahu.ahutong.ext

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.ahu.ahutong.core.common.AppContextHolder
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val GlobalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("CoroutineExceptionHandler", "协程异常: ${throwable::class.java} - ${throwable.message}")
    val app = AppContextHolder.getAppOrNull() ?: return@CoroutineExceptionHandler
    Handler(Looper.getMainLooper()).post {
        when (throwable) {
            is UnknownHostException -> {
                Toast.makeText(
                    app,
                    "网络不可用，请检查网络连接",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is SocketTimeoutException -> {
                Toast.makeText(
                    app,
                    "请求超时，请重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                Toast.makeText(
                    app,
                    "发生未知错误: ${throwable.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

fun CoroutineScope.launchSafe(
    block: suspend CoroutineScope.() -> Unit
) = launch(GlobalCoroutineExceptionHandler) {
    block()
}
