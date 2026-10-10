package com.ahu.ahutong.ui.screen.canteen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.data.recharge.analytics.CanteenLabelCandidate
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppSectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 必吃榜上传链路自检（**仅 debug 构建挂出**）。
 *
 * 为什么需要它：上传是**静默**的——失败只写 logcat，界面上什么都看不到。
 * 内测同学问「我这台机器到底传上去了没有」时，只有这个按钮能直接给答案。
 *
 * 刻意**复用同一条链路**（[CanteenGateway.uploadRecentBills] / [CanteenGateway.reportWindow]），
 * 不做任何替身实现——否则「自检通过而真实上传失败」就毫无意义。
 * 只把两个输入换成固定值：忽略"已处理的顿"（自检要能重复跑）、测试上报用假终端码。
 */
@Composable
fun CanteenUploadDiagnosticsCard(modifier: Modifier = Modifier) {
    var running by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()

    AppSectionCard(modifier = modifier) {
        Text("必吃榜上传自检", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "与真实上传走同一条链路：拉近 30 天账单 → 正餐过滤 → 分批上传 → 挑补标注候选。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppButton(
            onClick = {
                running = true
                lines = emptyList()
                scope.launch {
                    val sync = withContext(Dispatchers.IO) {
                        // 自检忽略"已处理的顿"，好让同一个候选能反复跑
                        CanteenGateway.uploadRecentBills(handledMeals = emptySet())
                    }
                    lines = if (sync.ok) {
                        listOf(
                            "① 拉账单：成功，${sync.fetched} 笔原始流水",
                            "② 正餐过滤后上传：${sync.uploaded} 笔，HTTP " +
                                if (sync.uploadOk) "成功" else "失败（看 logcat 的状态码）",
                            "③ 补标注候选：" + (
                                sync.ask?.let { "${it.terminal} · ${it.slotLabel} · ${it.merchant}" }
                                    ?: "无（窗口都已收录，或 24h 内没有未收录正餐）"
                                ),
                            "结论：链路通 ✅ 服务端是否真落库，去后台「上传链路监控」看你这条出口 IP 的包",
                        )
                    } else {
                        listOf(
                            "① 拉账单：失败（原地重试 3 次仍失败）",
                            "结论：链路断了 ❌ 先确认学习通已登录、网络可用；细节看 logcat 的 CanteenGateway 标签",
                        )
                    }
                    running = false
                }
            },
            enabled = !running,
            variant = AppButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) "上传中…" else "跑一次上传")
        }

        AppButton(
            onClick = {
                running = true
                lines = emptyList()
                scope.launch {
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                    val probe = CanteenLabelCandidate(
                        terminal = "__DEV_SELFTEST__",
                        ts = ts,
                        slotLabel = "自检",
                        amountCents = 0,
                        merchant = "开发者自检上报（可在后台拒绝）",
                        sampleCount = 1
                    )
                    // 固定 token：同一个人反复自检不会把上报人数刷上去
                    val ok = withContext(Dispatchers.IO) {
                        CanteenGateway.reportWindow(probe, "自检窗口", "debug-selftest")
                    }
                    lines = listOf(
                        if (ok) {
                            "测试上报：成功（204）✅ 去后台「待审上报」应能看到 __DEV_SELFTEST__，看完拒绝掉即可"
                        } else {
                            "测试上报：失败 ❌ 多半是 X-Api-Key 没配或网络不通（看 logcat 的状态码）"
                        }
                    )
                    running = false
                }
            },
            enabled = !running,
            variant = AppButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("发一条测试上报（进后台待审，可拒绝）")
        }

        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}
