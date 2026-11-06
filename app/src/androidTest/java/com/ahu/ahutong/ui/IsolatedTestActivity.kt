package com.ahu.ahutong.ui

import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry

/** HyperOS blocks an instrumented background UID even with Android's BAL permission. */
internal fun launchIsolatedTestActivity(): ActivityScenario<ComponentActivity> {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val component = "${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity"
    val result = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("am start -W -n $component")
    ).bufferedReader().use { it.readText() }
    check(!result.contains("Error")) { "Unable to start isolated test host: $result" }
    return ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
