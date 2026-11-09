package com.ahu.ahutong.ui.screen.assistant

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahu.ahutong.ui.glow.AssistantGlowState

/**
 * AI 界面激活期间把窗口改成 adjustNothing：键盘弹出不缩窗口，
 * 只靠 ime inset 把输入栏顶上去；关闭后恢复原来的 softInputMode，其他页面不受影响。
 *
 * 切后台再回来时部分系统会把 softInputMode 重置回 manifest 值，
 * 导致窗口 resize/pan 和我们的 IME 占位叠加，所以 ON_RESUME 再施加一次。
 */
@Composable
fun AssistantImeMode() {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val visible = AssistantGlowState.visible || AssistantChatState.visible
    DisposableEffect(activity, visible, lifecycleOwner) {
        val window = activity?.window
        val original = window?.attributes?.softInputMode
        fun applyAdjustNothing() {
            if (!visible || window == null) return
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            )
        }
        applyAdjustNothing()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applyAdjustNothing()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (visible && window != null && original != null) {
                window.setSoftInputMode(original)
            }
        }
    }
}
