package com.ahu.ahutong.ui.screen.laundry

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.data.laundry.LaundryCampus
import com.ahu.ahutong.data.laundry.LaundryDevice
import com.ahu.ahutong.data.laundry.LaundryKind
import com.ahu.ahutong.data.laundry.LaundryOccupancy
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppRefreshButton
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.haptics.HapticTextButton
import com.ahu.ahutong.ui.haptics.hapticClickable
import com.ahu.ahutong.ui.state.LaundryViewModel
import com.kyant.monet.withNight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 洗衣房空闲/占用总览。
 *
 * 磬苑走乐摇摇（离线台账 + 实时凭据），龙河走趣智校园匿名接口。
 */
@Composable
fun LaundryScreen(
    onBack: () -> Unit,
    viewModel: LaundryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showTokenSheet by remember { mutableStateOf(false) }
    var detailDevice by remember { mutableStateOf<LaundryDevice?>(null) }
    var pasted by remember { mutableStateOf("") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastClipboardText by remember { mutableStateOf("") }
    var previousToken by remember { mutableStateOf(state.token) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(state.hint) {
        val hint = state.hint ?: return@LaunchedEffect
        Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
        viewModel.consumeHint()
    }

    LaunchedEffect(state.token) {
        if (state.token != null && state.token != previousToken) {
            showTokenSheet = false
            pasted = ""
        }
        previousToken = state.token
    }

    // ON_RESUME 剪贴板接力：仅在磬苑且未持有效凭据时尝试一次，避免重复校验。
    DisposableEffect(lifecycleOwner, state.hasToken, state.campus) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !state.hasToken &&
                state.campus == LaundryCampus.QINGYUAN
            ) {
                val text = clipboard.getText()?.text
                if (!text.isNullOrBlank() && text != lastClipboardText) {
                    lastClipboardText = text
                    viewModel.applyToken(text)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val blocks = remember(state.rows, state.floors) {
        buildBlockList(state.rows, state.floors)
    }
    val parkOptions = remember(state.parks) {
        state.parks.mapIndexed { index, name -> AppSelectOption(index, name) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.parkIndex, state.kind) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.hasToken, state.campus) {
        if (!state.hasToken && state.campus != LaundryCampus.LONGHE) return@LaunchedEffect
        while (true) {
            viewModel.refresh()
            delay(45_000)
        }
    }

    AppPageScaffold(
        title = "洗衣房",
        subtitle = state.parks.getOrNull(state.parkIndex)?.let { "$it · ${state.rows.size} 台" },
        onBack = onBack,
        listState = listState,
        verticalArrangement = Arrangement.spacedBy(if (state.floors.isEmpty()) 16.dp else 12.dp),
        lazyContent = {
            item(key = "campus-and-park") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LaundryCampus.entries.forEach { campus ->
                            AppFilterChip(
                                selected = state.campus == campus,
                                onClick = { viewModel.selectCampus(campus) },
                                label = { Text(campus.label, fontSize = 13.sp) }
                            )
                        }
                    }
                    AppSelectField(
                        label = "楼栋",
                        selected = state.parkIndex.takeIf { it in state.parks.indices },
                        options = parkOptions,
                        onSelected = viewModel::selectPark,
                        placeholder = if (state.refreshing) "加载中" else "暂无可用",
                        enabled = state.parks.isNotEmpty(),
                        valueTextAlign = TextAlign.End,
                        miuixStandalone = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (state.campus == LaundryCampus.QINGYUAN) {
                item(key = "token-banner") {
                    TokenBanner(
                        state = state,
                        nowMillis = nowMillis,
                        onClick = { showTokenSheet = true }
                    )
                }
            }
            item(key = "kind-chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(LaundryKind.entries) { kind ->
                        AppFilterChip(
                            selected = state.kind == kind,
                            onClick = { viewModel.selectKind(kind) },
                            label = { Text(kind.label, fontSize = 13.sp) }
                        )
                    }
                }
            }
            val summaryText = buildStatusSummary(state)
            if (summaryText != null) {
                item(key = "status-summary") {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is LaundryBlock.Floor -> item(key = "floor-$index") {
                        FloorStrip(
                            floor = block.floor,
                            rows = block.rows,
                            nowMillis = nowMillis,
                            onDeviceClick = { detailDevice = it }
                        )
                    }
                    is LaundryBlock.Grid -> item(key = "grid-$index") {
                        GridRow(
                            rows = block.rows,
                            nowMillis = nowMillis,
                            onDeviceClick = { detailDevice = it }
                        )
                    }
                }
            }
            item(key = "footer") {
                Text(
                    text = if (state.campus == LaundryCampus.LONGHE) {
                        "共 ${state.rows.size} 台 · 数据源：趣智校园"
                    } else {
                        "共 ${state.rows.size} 台 · 数据源：乐摇摇"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        },
        trailingContent = {
            AppRefreshButton(
                state = if (state.refreshing) RefreshState.LOADING else RefreshState.IDLE,
                onClick = { viewModel.refresh() }
            )
        }
    )

    if (showTokenSheet) {
        AppModalBottomSheet(
            title = "导入凭据",
            modifier = Modifier.imePadding(),
            onDismissRequest = {
                showTokenSheet = false
                viewModel.clearTokenError()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "1. 分享链接到微信并打开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2. 点击右上角「···」复制链接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. 返回安大通自动导入，或在下方粘贴",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val firstDeviceId = state.rows.firstOrNull()?.device?.id
                AppButton(
                    onClick = {
                        if (firstDeviceId != null) {
                            viewModel.consumeHint()
                            shareToWeChat(context, firstDeviceId)
                        }
                    },
                    enabled = firstDeviceId != null,
                    variant = AppButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("分享链接到微信")
                }
                AppTextField(
                    value = pasted,
                    onValueChange = {
                        pasted = it
                        viewModel.clearTokenError()
                    },
                    label = "链接或凭据",
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(
                        onClick = {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrBlank()) pasted = text
                        },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("粘贴")
                    }
                    AppButton(
                        onClick = { viewModel.applyToken(pasted) },
                        enabled = pasted.isNotBlank() && !state.refreshing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.refreshing) "校验中" else "导入")
                    }
                }
                state.tokenError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.hasToken) {
                    HapticTextButton(
                        onClick = {
                            viewModel.clearToken()
                            showTokenSheet = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("清除当前凭据", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    text = "凭据仅存于本机，有效期 2 小时。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (state.parkPrompt.isNotEmpty()) {
        AppDialog(
            title = "选择洗衣房",
            onDismiss = { viewModel.dismissParkPrompt() },
            subtitle = state.matchNote,
            actions = listOf(
                AppDialogAction(label = "取消", onClick = { viewModel.dismissParkPrompt() })
            )
        ) {
            state.parkPrompt.forEach { index ->
                val park = state.parks.getOrNull(index) ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .hapticClickable { viewModel.selectPark(index) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(park, style = MaterialTheme.typography.bodyLarge)
                    if (index == state.parkIndex) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (detailDevice != null) {
        val device = detailDevice!!
        val parkName = state.parks.getOrNull(device.parkIndex).orEmpty()
        AppDialog(
            title = device.slot?.takeIf { it.isNotBlank() } ?: device.kind.label,
            onDismiss = { detailDevice = null },
            subtitle = parkName.takeIf { it.isNotBlank() },
            contentScrollable = true,
            actions = listOf(AppDialogAction(label = "关闭", onClick = { detailDevice = null }))
        ) {
            val rows = listOfNotNull(
                "设备编号" to device.id,
                "类型" to device.kind.label,
                "机位" to (device.slot ?: "—"),
                "位置" to (device.location ?: "—"),
                "楼层" to (device.floor?.let { "${it}F" } ?: "—"),
                "备注" to (device.remark ?: "—"),
                if (state.campus == LaundryCampus.QINGYUAN) {
                    "在线状态" to if (device.online) "在线" else "离线"
                } else {
                    null
                }
            )
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            state.rows.firstOrNull { it.device.id == device.id }?.occupancy?.let { occupancy ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "当前状态",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (occupancy) {
                            is LaundryOccupancy.Idle -> "空闲"
                            is LaundryOccupancy.InUse ->
                                remainingMinutes(occupancy.endAtMillis, occupancy.clockOffsetMillis, nowMillis)
                            is LaundryOccupancy.Unknown -> occupancy.reason.ifBlank { "未知" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun buildStatusSummary(state: LaundryViewModel.UiState): String? {
    val parts = buildList {
        state.matchNote?.takeIf { it.isNotBlank() }?.let(::add)
        if (state.hasToken || state.campus == LaundryCampus.LONGHE) {
            add("空闲 ${state.freeCount} · 使用中 ${state.busyCount}")
            state.updatedAt?.let {
                add(SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(it)) + " 更新")
            }
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun TokenBanner(
    state: LaundryViewModel.UiState,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val expired = state.tokenExpiresAt?.let { it <= nowMillis } ?: false
    val (title, detail) = when {
        !state.hasToken -> "导入乐摇摇凭据" to "未导入，无法查看实时状态"
        expired -> "凭据已过期" to "点击重新导入"
        else -> "凭据有效" to remainingText(state.tokenExpiresAt, nowMillis)
    }
    AppCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "›",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GridRow(
    rows: List<LaundryViewModel.DeviceRow>,
    nowMillis: Long,
    onDeviceClick: (LaundryDevice) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Box(modifier = Modifier.weight(1f)) {
                DeviceCell(
                    device = row.device,
                    occupancy = row.occupancy,
                    nowMillis = nowMillis,
                    onClick = { onDeviceClick(row.device) }
                )
            }
        }
        repeat(3 - rows.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun FloorStrip(
    floor: Int,
    rows: List<LaundryViewModel.DeviceRow>,
    nowMillis: Long,
    onDeviceClick: (LaundryDevice) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${floor}F",
            modifier = Modifier.width(30.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val cellWidth = ((maxWidth - 20.dp) / 3).coerceAtLeast(92.dp)
            val rowState = remember(floor) { LazyListState() }
            LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows.size) { index ->
                    val row = rows[index]
                    Box(modifier = Modifier.width(cellWidth)) {
                        DeviceCell(
                            device = row.device,
                            occupancy = row.occupancy,
                            nowMillis = nowMillis,
                            placeOverride = row.device.location?.takeIf { it.isNotBlank() } ?: "",
                            onClick = { onDeviceClick(row.device) }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface LaundryBlock {
    val size: Int

    data class Floor(val floor: Int, val rows: List<LaundryViewModel.DeviceRow>) : LaundryBlock {
        override val size: Int get() = rows.size
    }

    data class Grid(val rows: List<LaundryViewModel.DeviceRow>) : LaundryBlock {
        override val size: Int get() = rows.size
    }
}

private fun buildBlockList(
    rows: List<LaundryViewModel.DeviceRow>,
    floors: List<LaundryViewModel.FloorRow>
): List<LaundryBlock> {
    if (floors.isEmpty()) return rows.chunked(3).map { LaundryBlock.Grid(it) }
    return floors.map { LaundryBlock.Floor(it.floor, it.rows) }
}

@Composable
private fun DeviceCell(
    device: LaundryDevice,
    occupancy: LaundryOccupancy?,
    nowMillis: Long,
    placeOverride: String? = null,
    onClick: () -> Unit
) {
    val status = when (occupancy) {
        is LaundryOccupancy.Idle -> "空闲"
        is LaundryOccupancy.InUse ->
            remainingMinutes(occupancy.endAtMillis, occupancy.clockOffsetMillis, nowMillis)
        is LaundryOccupancy.Unknown -> occupancy.reason.ifBlank { "未知" }
        null -> "—"
    }
    val (fg, bg) = when (occupancy) {
        is LaundryOccupancy.Idle -> idleFg() to idleBg()
        is LaundryOccupancy.InUse -> busyFg() to busyBg()
        else -> mutedFg() to mutedBg()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .hapticClickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp)
    ) {
        val title = device.slot?.takeIf { it.isNotBlank() } ?: device.kind.label
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val place = placeOverride ?: buildString {
            device.location?.let { append(it) }
            device.floor?.let { append(it).append("F") }
            device.remark?.let { if (isNotEmpty()) append(" · "); append(it) }
        }
        if (place.isNotEmpty()) {
            Text(place, fontSize = 10.sp, color = fg.copy(alpha = 0.75f), maxLines = 1)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(status, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

private fun shareToWeChat(context: Context, deviceId: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "https://m.leyaoyao.com/customer/message/t/$deviceId"
        )
        setPackage("com.tencent.mm")
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "未检测到微信，请手动粘贴链接", Toast.LENGTH_SHORT).show()
        }
}

private fun remainingMinutes(endAtMillis: Long, offset: Long, nowMillis: Long): String {
    val seconds = ((endAtMillis - (nowMillis + offset)) / 1000).coerceAtLeast(0)
    return when {
        seconds <= 0 -> "即将结束"
        seconds > 60 -> "剩 ${(seconds + 59) / 60} 分"
        else -> "剩 ${seconds} 秒"
    }
}

private fun remainingText(expiresAtMillis: Long?, nowMillis: Long): String {
    val seconds = expiresAtMillis?.let { (it - nowMillis) / 1000 } ?: 0
    if (seconds <= 0) return "已过期"
    val minutes = seconds / 60
    return if (minutes >= 60) "剩余 ${minutes / 60} 小时 ${minutes % 60} 分" else "剩余 $minutes 分"
}

@Composable
private fun idleFg(): Color = Color(0xFF1B7A3D) withNight Color(0xFF6FD48A)

@Composable
private fun idleBg(): Color = Color(0xFFE7F6EC) withNight Color(0xFF16301F)

@Composable
private fun busyFg(): Color = Color(0xFFB3500A) withNight Color(0xFFF0A15C)

@Composable
private fun busyBg(): Color = Color(0xFFFFF1E2) withNight Color(0xFF33241A)

@Composable
private fun mutedFg(): Color = Color(0xFF8A8A8E) withNight Color(0xFF8E8E93)

@Composable
private fun mutedBg(): Color = Color(0xFFF2F2F5) withNight Color(0xFF242426)
