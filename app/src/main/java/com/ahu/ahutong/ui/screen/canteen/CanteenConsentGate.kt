package com.ahu.ahutong.ui.screen.canteen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 必吃榜匿名数据上传的同意弹窗（挂在主页）：consent 为 null（未表态）时弹一次。
 * 同意/拒绝都会落盘，之后不再弹；设置-偏好里随时可以改。
 *
 * 同时兼任「静默上传」触发点（已同意时）：
 * - 进程冷启动进主页触发一次
 * - 之后每次主页 ON_RESUME（回前台/从别的页返回）检查一次，距上次 ≥30 分钟才真跑
 * 全程无 UI，失败静默。
 */
@HiltViewModel
class CanteenConsentViewModel @Inject constructor(
    private val settings: SettingsStore
) : ViewModel() {

    /** null=加载中（不弹），true=需要弹，false=已表态。 */
    private val _shouldAsk = MutableStateFlow<Boolean?>(null)
    val shouldAsk: StateFlow<Boolean?> = _shouldAsk.asStateFlow()

    init {
        viewModelScope.launch {
            settings.canteenUploadConsent.collect { consent ->
                _shouldAsk.value = consent == null
                if (consent == true) maybeBackgroundSync()
            }
        }
    }

    /** 节流触发后台同步：距上次触发不足 30 分钟直接跳过；未同意不上传。 */
    fun maybeBackgroundSync() {
        val now = System.currentTimeMillis()
        val last = lastSyncAt.get()
        if (now - last < SYNC_THROTTLE_MS) return
        if (!lastSyncAt.compareAndSet(last, now)) return
        viewModelScope.launch(Dispatchers.IO) {
            if (settings.canteenUploadConsent.first() == true) {
                CanteenGateway.uploadRecentBills()
            }
        }
    }

    fun answer(consent: Boolean) {
        viewModelScope.launch { settings.setCanteenUploadConsent(consent) }
    }

    companion object {
        /** 上次触发时间（进程内）：冷启动为 0 必触发；之后 30 分钟节流。 */
        private val lastSyncAt = java.util.concurrent.atomic.AtomicLong(0)
        private const val SYNC_THROTTLE_MS = 30 * 60 * 1000L
    }
}

@Composable
fun CanteenConsentGate(viewModel: CanteenConsentViewModel = hiltViewModel()) {
    val shouldAsk by viewModel.shouldAsk.collectAsState()

    // 回前台/返回主页时检查节流同步（节流逻辑在 VM 内，未同意时恒不上传）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.maybeBackgroundSync()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (shouldAsk != true) return

    AppDialog(
        title = "参与「安大必吃榜」共建？",
        onDismiss = { viewModel.answer(false) },
        actions = listOf(
            AppDialogAction("暂不参与", onClick = { viewModel.answer(false) }),
            AppDialogAction(
                "匿名参与",
                onClick = { viewModel.answer(true) },
                style = AppDialogActionStyle.Primary
            )
        ),
        content = {
            androidx.compose.material3.Text(
                "新版上线「必吃榜」：汇总全校同学在哪个食堂窗口吃饭的热度。\n\n" +
                    "若你同意参与，App 会在打开时上传你名下食堂消费的**去标识记录**" +
                    "（POS 机号、时间、金额），用于计算窗口排行榜。\n\n" +
                    "数据不含学号、姓名、卡号等任何个人信息，服务器也无法把记录关联到你。" +
                    "可以随时在 设置 → 偏好设置 中关闭。"
            )
        }
    )
}
