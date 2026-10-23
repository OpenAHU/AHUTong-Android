package com.ahu.ahutong

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.widget.Toast
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.UserNotice
import com.ahu.ahutong.data.toAhuError
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.error.FailurePresentation
import com.ahu.ahutong.error.failurePresentation
import com.ahu.ahutong.error.UnexpectedErrorReportFormatter
import com.ahu.ahutong.error.UnexpectedErrors

/**
 * 生产提示出口：已知失败保持普通提示，意外异常携带脱敏追踪交给前台弹窗。
 */
object ToastUserNotice : UserNotice {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    override fun showFailure(error: Throwable) {
        if (reportUnexpected(error)) return
        val message = error.toAhuError().toUserMessage()
        mainHandler.post {
            Toast.makeText(appContext ?: AppEnvironmentHolder.context(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun reportUnexpected(error: Throwable): Boolean = when (failurePresentation(error)) {
        FailurePresentation.Ignore -> true
        FailurePresentation.Expected -> false
        FailurePresentation.Unexpected -> {
            val environment = "AHUTong ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
                "设备 ${Build.MANUFACTURER} ${Build.MODEL}\n构建 ${BuildConfig.BUILD_TYPE}"
            UnexpectedErrors.queue.enqueue(UnexpectedErrorReportFormatter.format(error, environment))
            true
        }
    }
}
