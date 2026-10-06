package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.ahu.ahutong.R
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.state.BillingViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.util.Locale

/**
 * 账单（校园卡交易流水）：本月收支汇总 + 分页流水列表。
 * 金额：分→元整数运算后格式化；支出 -红系 / 收入 +绿系以外的中性色（与充值记录区分）。
 * 时间：直接用服务端本地化的 effectdateStr。
 */
@Composable
fun Billing(
    onBack: () -> Unit,
    onOpenStats: () -> Unit = {},
    viewModel: BillingViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    var detailRecord by remember { mutableStateOf<TurnoverRecord?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    AppPageScaffold(
        title = "账单",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        actions = listOf(
            TrailingAction(painterResource(R.drawable.ic_billing_analysis), "统计") { onOpenStats() },
            TrailingAction(painterResource(R.drawable.ic_billing_filter), "筛选") { showFilter = true },
            TrailingAction(painterResource(R.drawable.ic_billing_refresh), "刷新") { viewModel.refresh() }
        ),
        lazyContent = {
            // 汇总头
            item(key = "summary") {
                BillingSummaryCard(
                    expensesFen = summary?.expenses,
                    incomeFen = summary?.income
                )
            }

            when (val state = listState) {
                is BillingViewModel.ListState.Loading -> {
                    item(key = "loading") {
                        AppStateCard.Loading(message = "账单加载中…")
                    }
                }
                is BillingViewModel.ListState.Error -> {
                    item(key = "error") {
                        AppStateCard.Error(
                            message = state.message,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                }
                is BillingViewModel.ListState.Ready -> {
                    if (records.isEmpty()) {
                        item(key = "empty") {
                            AppStateCard.Empty(
                                message = "本月暂无账单记录",
                                subtitle = "校园卡消费与充值流水将在这里展示"
                            )
                        }
                    } else {
                        // 细则收进同一张卡片，条目间分隔线（不再一条一卡）
                        item(key = "list") {
                            AppCard(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                records.forEachIndexed { index, record ->
                                    BillingRecordItem(
                                        record = record,
                                        onClick = { detailRecord = record }
                                    )
                                    if (index < records.lastIndex) {
                                        androidx.compose.material3.HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = 92.n1 withNight 22.n1
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "footer") {
                            BillingListFooter(
                                hasMore = hasMore,
                                loadingMore = loadingMore,
                                onLoadMore = { viewModel.loadNextPage() }
                            )
                        }
                    }
                }
            }
        }
    )

    detailRecord?.let { record ->
        BillingDetailDialog(record = record, onDismiss = { detailRecord = null })
    }

    if (showFilter) {
        BillingFilterSheet(
            viewModel = viewModel,
            onDismiss = { showFilter = false }
        )
    }
}

/** 筛选弹窗：类型 + 金额两栏目，可交集，再次点击置空即全选。 */
@Composable
private fun BillingFilterSheet(
    viewModel: BillingViewModel,
    onDismiss: () -> Unit
) {
    val initAllTime by viewModel.allTime.collectAsState()
    val initType by viewModel.typeFilter.collectAsState()
    val initAmount by viewModel.amountRange.collectAsState()

    var allTimeSel by remember { mutableStateOf(initAllTime) }
    var typeSel by remember { mutableStateOf(initType) }
    var minText by remember {
        mutableStateOf(initAmount.first?.let { fenToYuanText(it) } ?: "")
    }
    var maxText by remember {
        mutableStateOf(initAmount.second?.let { fenToYuanText(it) } ?: "")
    }

    AppDialog(
        title = "筛选账单",
        onDismiss = onDismiss,
        actions = listOf(
            AppDialogAction(
                "清空全部",
                onClick = {
                    viewModel.applyFilters(false, null, null, null)
                    onDismiss()
                }
            ),
            AppDialogAction(
                "应用",
                style = AppDialogActionStyle.Primary,
                onClick = {
                    viewModel.applyFilters(
                        allTimeSel,
                        typeSel,
                        minText.toDoubleOrNull()?.let { (it * 100).toLong() },
                        maxText.toDoubleOrNull()?.let { (it * 100).toLong() }
                    )
                    onDismiss()
                }
            )
        ),
        content = {
            Text(
                text = "时间",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "当月", true to "全部时间").forEach { (value, label) ->
                    AppFilterChip(
                        selected = allTimeSel == value,
                        onClick = { allTimeSel = value },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                text = "类型",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to "支出", false to "收入").forEach { (value, label) ->
                    AppFilterChip(
                        selected = typeSel == value,
                        onClick = { typeSel = if (typeSel == value) null else value },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                text = "金额（元）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = "最低",
                    modifier = Modifier.weight(1f)
                )
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = "最高",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    )
}

/** 分 → 元文本（整数元不带小数点）。 */
private fun fenToYuanText(fen: Long): String {
    val yuan = fen / 100.0
    return if (yuan % 1.0 == 0.0) yuan.toLong().toString() else yuan.toString()
}

/** 本月收支汇总头卡。金额单位：分。 */
@Composable
private fun BillingSummaryCard(expensesFen: Long?, incomeFen: Long?) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "本月支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${formatFen(expensesFen)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = 40.a1 withNight 75.a1
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "本月收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${formatFen(incomeFen)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BillingRecordItem(record: TurnoverRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                if (record.isExpense) R.drawable.ic_bill_expense else R.drawable.ic_bill_income
            ),
            contentDescription = if (record.isExpense) "支出" else "收入",
            modifier = Modifier.size(18.dp),
            // 支出马卡龙红 / 收入马卡龙绿（硬编码，不随主题取色）
            tint = if (record.isExpense) MacaronRed else MacaronGreen
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.toMerchant?.trim().orEmpty().ifBlank {
                        record.resume.orEmpty()
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.effectdateStr.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${if (record.isExpense) "-" else "+"}${formatFen(record.tranamt)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (record.isExpense) {
                    40.a1 withNight 75.a1
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
private fun BillingListFooter(
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (!hasMore) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "已加载全部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    // 进入组合即加载下一页（LazyColumn 滚动到底时 footer 入屏触发）
    LaunchedEffect(Unit) { onLoadMore() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (loadingMore) {
            com.ahu.ahutong.ui.components.AppCircularProgressIndicator()
        }
    }
}

/** 单笔详情弹窗（H5 详情页的字段集合）。 */
@Composable
private fun BillingDetailDialog(record: TurnoverRecord, onDismiss: () -> Unit) {
    AppDialog(
        title = record.toMerchant?.trim()?.ifBlank { null } ?: "交易详情",
        onDismiss = onDismiss,
        actions = listOf(AppDialogAction("关闭", onClick = onDismiss)),
        content = {
            BillingDetailRow("金额", "${if (record.isExpense) "-" else "+"}¥${formatFen(record.tranamt)}")
            BillingDetailRow("类型", record.consumeTypeName ?: record.turnoverType.orEmpty())
            BillingDetailRow("时间", record.effectdateStr.orEmpty())
            BillingDetailRow("交易后余额", "¥${formatFen(record.cardBalance)}")
            // POS 终端号：必吃榜映射表踩点用的关键信息，吃完当场看这条就能报号
            record.locationName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                BillingDetailRow("POS 机号", it)
            }
            record.resume?.takeIf { it.isNotBlank() }?.let { BillingDetailRow("摘要", it) }
            if (record.feeAmt > 0) BillingDetailRow("手续费", "¥${formatFen(record.feeAmt)}")
        }
    )
}

@Composable
private fun BillingDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f)
        )
    }
}

/** 分 → 元，整数运算避免浮点误差，保留两位。 */
private val MacaronRed = Color(0xFFE87897)
private val MacaronGreen = Color(0xFF95D5B2)

private fun formatFen(fen: Long?): String {
    val safe = fen ?: 0
    return String.format(Locale.CHINA, "%d.%02d", safe / 100, safe % 100)
}
