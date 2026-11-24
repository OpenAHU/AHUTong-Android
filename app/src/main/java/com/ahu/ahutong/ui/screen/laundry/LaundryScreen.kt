package com.ahu.ahutong.ui.screen.laundry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu.ahutong.data.laundry.LaundryKind
import com.ahu.ahutong.data.laundry.LaundryOccupancy
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppRefreshButton
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.ui.state.LaundryViewModel
import com.kyant.monet.withNight
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 洗衣房空闲/占用总览。
 *
 * 台账 assets 基线 + 线上增量（ETag/304）；实时占用要 token（微信静默授权产物，2 小时过期）。
 * 无感接力：页面复制分享链接 → 微信打开授权 → 切回 App 自动读剪贴板接上；
 * 也可打开弹层手动粘贴。占用查询按园区整查、分批回填，先到先显示。
 */
@Composable
fun LaundryScreen(
    onBack: () -> Unit,
    viewModel: LaundryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showTokenSheet by remember { mutableStateOf(false) }
    var showParkSheet by remember { mutableStateOf(false) }
    var detailDevice by remember { mutableStateOf<com.ahu.ahutong.data.laundry.LaundryDevice?>(null) }
    var pasted by remember { mutableStateOf("") }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastClipboardText by remember { mutableStateOf("") }

    // 倒计时与「剩余几点几分」都靠这个心跳推进，不需要每秒请求
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // ON_RESUME 剪贴板接力：从微信回来时自动读。只认「看着像 token 的文本」，
    // 且同一文本只消费一次，避免反复触发网络校验。
    DisposableEffect(lifecycleOwner, state.hasToken) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !state.hasToken) {
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

    // 列表条目：有楼层信息的园区按楼层分组（每层一行，行内横向可滑），
    // 无楼层的园区仍是每行 3 台的平铺块。
    val blocks = remember(state.rows, state.floors) {
        buildBlockList(state.rows, state.floors)
    }

    val listState = rememberLazyListState()
    // 换园区/机型后条目数完全不同，留在原下标会落到无关段落，直接回顶
    LaunchedEffect(state.parkIndex, state.kind) {
        listState.scrollToItem(0)
    }
    // 页面停留期间每 45 秒自动刷一次（按当前可见窗口），用户不操作也能看到状态变化
    LaunchedEffect(state.parkIndex, state.kind, state.hasToken) {
        if (!state.hasToken) return@LaunchedEffect
        while (true) {
            delay(45_000)
            viewModel.refresh()
        }
    }

    AppPageScaffold(
        title = "洗衣房",
        subtitle = state.parks.getOrNull(state.parkIndex)?.let { "$it · ${state.rows.size} 台" },
        onBack = onBack,
        listState = listState,
        // 楼层分组时条目密集（兰园 80+ 行），行间距收紧；平铺沿用默认留白
        verticalArrangement = Arrangement.spacedBy(if (state.floors.isEmpty()) 24.dp else 12.dp),
        lazyContent = {
            item {
                TokenBanner(
                    state = state,
                    nowMillis = nowMillis,
                    onClick = { showTokenSheet = true }
                )
            }
            item {
                val kinds = com.ahu.ahutong.data.laundry.LaundryKind.entries
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    itemsIndexed(kinds) { _, kind ->
                        AppFilterChip(
                            selected = state.kind == kind,
                            onClick = { viewModel.selectKind(kind) },
                            label = { Text(kind.label, fontSize = 13.sp) }
                        )
                    }
                }
            }
            if (state.hasToken) {
                item {
                    Text(
                        text = buildString {
                            append("空闲 ${state.freeCount} · 使用中 ${state.busyCount}")
                            state.updatedAt?.let {
                                append(" · 更新于 ")
                                append(SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(it)))
                            }
                        },
                        fontSize = 12.sp,
                        color = subtitleColor(),
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
            item {
                Text(
                    text = buildString {
                        append("台账 ${state.rows.size} 台 · ")
                        state.tableUpdatedAt?.let { append(it).append(" 更新 · ") }
                        append("状态来自乐摇摇平台")
                    },
                    fontSize = 11.sp,
                    color = subtitleColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        },
        actions = listOf(
            TrailingAction(Icons.Outlined.Settings, "选择园区") { showParkSheet = true }
        ),
        trailingContent = {
            AppRefreshButton(
                state = if (state.refreshing) RefreshState.LOADING else RefreshState.IDLE,
                onClick = { viewModel.refresh() }
            )
        }
    )

    if (showTokenSheet) {
        AppModalBottomSheet(
            title = "连接乐摇摇账号",
            // enableEdgeToEdge 下 adjustResize 不会缩小窗口，必须自己吃 IME inset，
            // 否则键盘一弹出就把输入框和「保存并刷新」全盖住（这就是点不到确认的原因）。
            modifier = Modifier.imePadding(),
            onDismissRequest = {
                showTokenSheet = false
                viewModel.clearTokenError()
            }
        ) {
            // heightIn + verticalScroll：小屏幕/键盘弹出时内容再长也能滑到底部按下按钮
            // （仓库里 StudentMailScreen 同样是这个写法）。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "占用状态只有乐摇摇平台的微信登录态能查（有效期 2 小时）。三步接上：" +
                        "① 点下方「分享链接到微信」，在微信里打开并等页面加载出来；" +
                        "② 点微信右上角「···」→「复制链接」——token 就在这条链接里；" +
                        "③ 切回安大通，token 自动接上，不用手动粘贴。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = subtitleColor()
                )
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
                    Text("分享链接到微信（推荐）")
                }
                Text(
                    "或手动：在微信里打开任一洗衣机页面 → 右上角「···」→「复制链接」→ 回来粘贴。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = subtitleColor()
                )
                // token 链接动辄 600+ 字符，多行不设上限会把内容顶出屏幕、按钮滚不到。
                // 固定最多 3 行，超出的部分由输入框内部滚动，保证「保存并刷新」始终可点。
                AppTextField(
                    value = pasted,
                    onValueChange = {
                        pasted = it
                        viewModel.clearTokenError()
                    },
                    label = "粘贴微信复制来的链接",
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
                        Text("从剪贴板粘贴")
                    }
                    AppButton(
                        onClick = { viewModel.applyToken(pasted) },
                        enabled = pasted.isNotBlank() && !state.refreshing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.refreshing) "校验中…" else "保存并刷新")
                    }
                }
                state.tokenError?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "token 仅加密保存在本机，不上传服务器，也不会出现在反馈日志里。",
                    fontSize = 11.sp,
                    color = subtitleColor()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // 园区选择：不常调的东西收进二级菜单（右上角设置图标）。
    // 列表滚动 + 点选即关；副标题实时反映当前园区。
    if (showParkSheet) {
        AppModalBottomSheet(
            title = "选择园区",
            onDismissRequest = { showParkSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.parks.forEachIndexed { index, park ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.selectPark(index)
                                showParkSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(park, fontSize = 15.sp)
                        if (index == state.parkIndex) {
                            Text(
                                "当前",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // 设备详情：核对现实机器编号（机身上贴的号）与台账字段是否一致。
    if (detailDevice != null) {
        val device = detailDevice!!
        val parkName = state.parks.getOrNull(device.parkIndex) ?: ""
        AppDialog(
            title = device.slot?.takeIf { it.isNotBlank() } ?: device.kind.label,
            onDismiss = { detailDevice = null },
            subtitle = "$parkName · 台账字段一览",
            contentScrollable = true,
            actions = listOf(AppDialogAction(label = "知道了", onClick = { detailDevice = null }))
        ) {
            val rows = listOfNotNull(
                "设备编号" to device.id,
                "类型" to device.kind.label,
                "机位" to (device.slot ?: "—"),
                "位置" to (device.location ?: "—"),
                "楼层" to (device.floor?.let { "${it}F" } ?: "—"),
                "备注" to (device.remark ?: "—"),
                "台账在线" to if (device.online) "是（扫描时在线）" else "否（扫描时离线）"
            )
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, fontSize = 13.sp, color = subtitleColor())
                    Text(
                        value,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 占用状态实时值也带上，方便对照
            state.rows.firstOrNull { it.device.id == device.id }?.occupancy?.let { occupancy ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("当前状态", fontSize = 13.sp, color = subtitleColor())
                    Text(
                        when (occupancy) {
                            is com.ahu.ahutong.data.laundry.LaundryOccupancy.Idle -> "空闲"
                            is com.ahu.ahutong.data.laundry.LaundryOccupancy.InUse ->
                                remainingMinutes(occupancy.endAtMillis, occupancy.clockOffsetMillis, nowMillis)
                            is com.ahu.ahutong.data.laundry.LaundryOccupancy.Unknown ->
                                occupancy.reason ?: "未知"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenBanner(
    state: LaundryViewModel.UiState,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val expired = state.tokenExpiresAt?.let { it <= nowMillis } ?: false
    val (title, detail) = when {
        !state.hasToken -> "未连接 · 点此开通实时空闲查看" to "机器位置不需要连接；每台在用/空闲要按弹层步骤连上微信才有"
        expired -> "token 已过期 · 点此续一次" to "去微信重新打开设备页 ··· 复制一次链接，切回自动续上"
        else -> "已连接" to (remainingText(state.tokenExpiresAt, nowMillis) + " · 下拉或切园区自动刷新")
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
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(3.dp))
                Text(detail, fontSize = 12.sp, color = subtitleColor())
            }
            if (!state.hasToken || expired) {
                Text("›", fontSize = 20.sp, color = subtitleColor())
            }
        }
    }
}

/** 平铺块：没有楼层信息的园区，每行 3 台。 */
@Composable
private fun GridRow(
    rows: List<LaundryViewModel.DeviceRow>,
    nowMillis: Long,
    onDeviceClick: (com.ahu.ahutong.data.laundry.LaundryDevice) -> Unit
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

/** 一层楼：左侧楼层号，右侧横向可滑的机器列表（三台以内正好铺满一行）。 */
@Composable
private fun FloorStrip(
    floor: Int,
    rows: List<LaundryViewModel.DeviceRow>,
    nowMillis: Long,
    onDeviceClick: (com.ahu.ahutong.data.laundry.LaundryDevice) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${floor}F",
            modifier = Modifier.width(30.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = subtitleColor()
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // 单元格按三列取整，超过三台的楼层横向滑动查看
            val cellWidth = ((maxWidth - 20.dp) / 3).coerceAtLeast(92.dp)
            // 按楼层记状态：LazyColumn 复用条目槽位，不按楼层区分会继承上一层的横向偏移
            val rowState = remember(floor) { LazyListState() }
            LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows.size) { index ->
                    val row = rows[index]
                    Box(modifier = Modifier.width(cellWidth)) {
                        DeviceCell(
                            device = row.device,
                            occupancy = row.occupancy,
                            nowMillis = nowMillis,
                            // 楼层已由行首标题给出：有方位就标方位（东/中/西），否则不占位
                            placeOverride = row.device.location?.takeIf { it.isNotBlank() } ?: "",
                            onClick = { onDeviceClick(row.device) }
                        )
                    }
                }
            }
        }
    }
}

/** 列表条目：一层楼（横向可滑）/ 平铺三台。 */
private sealed interface LaundryBlock {
    val size: Int

    data class Floor(val floor: Int, val rows: List<LaundryViewModel.DeviceRow>) : LaundryBlock {
        override val size: Int get() = rows.size
    }

    data class Grid(val rows: List<LaundryViewModel.DeviceRow>) : LaundryBlock {
        override val size: Int get() = rows.size
    }
}

/** 有楼层信息就每层一行，否则退回每行三台。 */
private fun buildBlockList(
    rows: List<LaundryViewModel.DeviceRow>,
    floors: List<LaundryViewModel.FloorRow>
): List<LaundryBlock> {
    if (floors.isEmpty()) return rows.chunked(3).map { LaundryBlock.Grid(it) }
    return floors.map { LaundryBlock.Floor(it.floor, it.rows) }
}

@Composable
private fun DeviceCell(
    device: com.ahu.ahutong.data.laundry.LaundryDevice,
    occupancy: com.ahu.ahutong.data.laundry.LaundryOccupancy?,
    nowMillis: Long,
    placeOverride: String? = null,
    onClick: () -> Unit
) {
    val status = when (occupancy) {
        is com.ahu.ahutong.data.laundry.LaundryOccupancy.Idle -> "空闲"
        is com.ahu.ahutong.data.laundry.LaundryOccupancy.InUse ->
            remainingMinutes(occupancy.endAtMillis, occupancy.clockOffsetMillis, nowMillis)
        is com.ahu.ahutong.data.laundry.LaundryOccupancy.Unknown ->
            occupancy.reason ?: "未知"
        null -> "—"
    }
    val (fg, bg) = when (occupancy) {
        is com.ahu.ahutong.data.laundry.LaundryOccupancy.Idle -> idleFg() to idleBg()
        is com.ahu.ahutong.data.laundry.LaundryOccupancy.InUse -> busyFg() to busyBg()
        else -> mutedFg() to mutedBg()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp)
    ) {
        // 洗鞋机 slot=null：标题只写类型名，不显示 null
        val title = device.slot?.takeIf { it.isNotBlank() } ?: device.kind.label
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 位置行：平铺时给出方位+楼层（11号楼的 A1、南7号楼的 3F），楼层分组时楼层由行标题承担
        val place = placeOverride ?: buildString {
            device.location?.let { append(it) }
            device.floor?.let { append(it).append("F") }
            device.remark?.let { if (isNotEmpty()) append(" · "); append(it) }
        }
        if (place.isNotEmpty()) {
            Text(place, fontSize = 10.sp, color = fg.copy(alpha = 0.75f), maxLines = 1)
        } else if (placeOverride == null) {
            // 平铺模式没有位置信息时用类型名占位；分组模式位置由行标题给出，不再重复
            Text(device.kind.label, fontSize = 10.sp, color = fg.copy(alpha = 0.75f), maxLines = 1)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(status, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

/** 分享设备链接到微信：ACTION_SEND + 指定包名直接进微信聊天选择页。 */
private fun shareToWeChat(context: android.content.Context, deviceId: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            android.content.Intent.EXTRA_TEXT,
            "https://m.leyaoyao.com/customer/message/t/$deviceId"
        )
        setPackage("com.tencent.mm")
    }
    runCatching { context.startActivity(intent) }
        .onFailure { it.printStackTrace() } // 没装微信 / 被禁 → 用户走手动粘贴路径
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
    return if (minutes >= 60) "剩 ${minutes / 60} 小时 ${minutes % 60} 分" else "剩 $minutes 分"
}

@Composable
private fun subtitleColor(): Color = Color(0xFF8A8A8E) withNight Color(0xFF8E8E93)

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
