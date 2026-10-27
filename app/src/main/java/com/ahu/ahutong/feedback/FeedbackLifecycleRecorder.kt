package com.ahu.ahutong.feedback

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics

/** Records destination names only, never view contents or Intent arguments. */
internal object FeedbackLifecycleRecorder : Application.ActivityLifecycleCallbacks {
    fun install(application: Application) {
        FeedbackDiagnostics.setOnClearListener { AppFeedbackDraft.clearIfCreated() }
        FeedbackDiagnostics.recordRuntime("应用启动 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) / ${BuildConfig.BUILD_TYPE}")
        application.registerActivityLifecycleCallbacks(this)
    }
    override fun onActivityResumed(activity: Activity) {
        FeedbackDiagnostics.recordRuntime("打开页面 ${activity.javaClass.simpleName}")
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
