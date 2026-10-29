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
import com.ahu.ahutong.error.AppErrorHandler
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics

/**
 * 生产提示出口：已知失败保持普通提示，意外异常携带脱敏追踪交给前台弹窗。
 */
object ToastUserNotice : UserNotice {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    override fun reportUnhandled(error: Throwable) {
        if (error is VirtualMachineError || error is LinkageError || error is ThreadDeath) {
            val thread = Thread.currentThread()
            thread.uncaughtExceptionHandler?.uncaughtException(thread, error) ?: throw error
            return
        }
        // Unexpected failures are captured by reportUnexpected; expected failures still need
        // their trace when they escaped a root coroutine instead of a normal business catch.
        if (failurePresentation(error) == FailurePresentation.Expected) {
            AppErrorHandler.captureHandled(error, "coroutine")
        }
        showFailure(error)
    }

    override fun showFailure(error: Throwable) {
        if (reportUnexpected(error)) return
        val message = error.toAhuError().toUserMessage()
        FeedbackDiagnostics.recordRuntime("业务提示：$message")
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
            val details = UnexpectedErrorReportFormatter.format(error, environment)
            val eventId = AppErrorHandler.captureHandled(error, "handled")
            UnexpectedErrors.queue.enqueue(details, eventId)
            true
        }
    }
}
