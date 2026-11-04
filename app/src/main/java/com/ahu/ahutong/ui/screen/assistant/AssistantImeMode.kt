package com.ahu.ahutong.ui.screen.assistant

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.ui.glow.AssistantGlowState

/**
 * AI 界面激活期间把窗口改成 adjustNothing：键盘弹出不缩窗口，
 * 只靠 ime inset 把输入栏顶上去；关闭后恢复原来的 softInputMode，其他页面不受影响。
 */
@Composable
fun AssistantImeMode() {
    val activity = LocalContext.current as? Activity
    val visible = AssistantGlowState.visible
    DisposableEffect(activity, visible) {
        val window = activity?.window
        val original = window?.attributes?.softInputMode
        if (visible && window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        onDispose {
            if (visible && window != null && original != null) {
                window.setSoftInputMode(original)
            }
        }
    }
}
