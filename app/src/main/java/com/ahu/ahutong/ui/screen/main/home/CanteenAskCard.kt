package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.screen.canteen.CanteenConsentViewModel
import com.ahu.ahutong.ui.screen.canteen.CanteenSubmitState
import com.ahu.ahutong.ui.screen.canteen.money

/**
 * 众包补标注卡片（主页，与校园卡同级）。
 *
 * 为什么是卡片不是弹窗：用户最常见的路径是「打开 App → 扫码 → 关掉」，弹窗会恰好卡在扫码前那几秒。
 * 卡片不抢焦点、不打断，用户什么时候想填再填；**没有可问的候选时整张卡不出现**（不占位）。
 *
 * 语义（2026-10-08 用户拍板）：
 * - 只问「最近 24 小时内、最新的、终端码不在映射表里」的那一顿，一次一张卡
 * - 「不记得」**不上报**，只静音这一顿；同一窗口以后的新一顿照常问，不封禁终端、不关闭功能
 * - 候选来源 [CanteenGateway.uploadRecentBills]（与账单同步共用同一批数据，不额外拉账单）
 */
@Composable
fun CanteenAskCard(modifier: Modifier = Modifier) {
    val viewModel: CanteenConsentViewModel = hiltViewModel()
    val ask by viewModel.pendingAsk.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val current = ask ?: return

    // 换一顿就重置输入框（mealKey 变了说明换题了）
    var input by remember(current.mealKey) { mutableStateOf("") }

    val busy = submitState == CanteenSubmitState.Submitting
    val done = submitState == CanteenSubmitState.Done

    AppSectionCard(modifier = modifier) {
        Text(
            text = if (done) "谢谢！" else "帮我们认一个窗口",
            style = MaterialTheme.typography.titleMedium
        )

        if (done) {
            Text(
                text = "已经记下「${input}」了，审核通过后大家都能在榜单里看到。",
                style = MaterialTheme.typography.bodyMedium
            )
            return@AppSectionCard
        }

        Text(
            text = "${current.dayLabel}${current.dayPart}${current.ts.take(16).drop(11)}" +
                " · ${current.canteen}${current.floor.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "${current.terminal} 号窗口 · ${current.amountCents.money()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppTextField(
            value = input,
            onValueChange = {
                if (it.length <= CanteenGateway.WINDOW_NAME_MAX_LEN) input = it
            },
            label = "这个窗口叫什么？（如 烤盘饭）",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppButton(
                onClick = viewModel::skipLabel,
                enabled = !busy,
                variant = AppButtonVariant.Secondary,
                modifier = Modifier.weight(1f)
            ) {
                Text("不记得")
            }
            AppButton(
                onClick = { viewModel.submitLabel(input) },
                enabled = input.isNotBlank() && !busy,
                variant = AppButtonVariant.Primary,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (busy) "提交中…" else "提交")
            }
        }

        // 失败不丢贡献：卡片留着、输入留着，只多一句提示让用户再点一次
        if (submitState == CanteenSubmitState.Failed) {
            Text(
                text = "提交失败，请检查网络后重试（内容已保留）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = "只会把你填的窗口名上报（公共信息，不含你的任何账号数据），审核通过后大家都能看到。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
