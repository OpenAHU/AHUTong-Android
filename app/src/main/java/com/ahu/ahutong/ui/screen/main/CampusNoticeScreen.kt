package com.ahu.ahutong.ui.screen.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.notice.AcademicAffairsNoticeParser
import com.ahu.ahutong.data.notice.CampusNotice
import com.ahu.ahutong.data.notice.CampusNoticeRepository
import com.ahu.ahutong.data.notice.CampusNoticeUrlPolicy
import com.ahu.ahutong.data.notice.CollegeNoticeRegistry
import com.ahu.ahutong.data.notice.PostgraduateNoticeParser
import com.ahu.ahutong.data.notice.UniversityNoticeParser
import com.ahu.ahutong.data.notice.noticeSourceLabel
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetPlacement
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusNoticeScreen() {
    val context = LocalContext.current
    val accountId = SessionStore.currentUser()?.xh
    val graduate = !AHUCache.canUseUndergraduateAcademics()
    val stored by CampusNoticeRepository.snapshot.collectAsState()
    val syncingAccountId by CampusNoticeRepository.syncingAccountId.collectAsState()
    val loadingMoreAccountId by CampusNoticeRepository.loadingMoreAccountId.collectAsState()
    val snapshot = stored?.takeIf { it.accountId == accountId }
    val isRefreshing = !accountId.isNullOrBlank() && syncingAccountId == accountId
    val isLoadingMore = !accountId.isNullOrBlank() && loadingMoreAccountId == accountId
    val allNotices = remember(snapshot?.notices) {
        snapshot?.notices.orEmpty().sortedWith(
            compareByDescending<CampusNotice> { it.publishedOn }
                .thenByDescending { it.discoveredAtMillis }
        )
    }
    val sourceOptions = remember(graduate, accountId) {
        buildList<Pair<String?, String>> {
            add(null to "全部")
            add(UniversityNoticeParser.SOURCE_ID to "校级")
            add(AcademicAffairsNoticeParser.SOURCE_ID to "教务处")
            if (CollegeNoticeRegistry.resolveCurrent() != null) {
                add(CollegeNoticeRegistry.SOURCE_ID to "学院")
            }
            if (graduate) add(PostgraduateNoticeParser.SOURCE_ID to "研究生院")
        }
    }
    var sourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val notices = remember(allNotices, sourceFilter) {
        if (sourceFilter == null) allNotices else allNotices.filter { it.sourceId == sourceFilter }
    }
    val activeSourceIds = remember(sourceFilter, sourceOptions) {
        if (sourceFilter != null) {
            listOf(sourceFilter)
        } else {
            sourceOptions.mapNotNull { it.first }
        }
    }
    val hasMore = remember(snapshot, activeSourceIds) {
        snapshot != null && activeSourceIds.any { id ->
            val status = snapshot.sourceStatuses[id]
            status != null && !status.historyExhausted &&
                (status.nextPageUrl != null || status.lastSuccessfulSyncDate != null)
        }
    }
    val historyError = remember(snapshot, activeSourceIds) {
        activeSourceIds.any { snapshot?.sourceStatuses?.get(it)?.historyError != null }
    }
    LaunchedEffect(sourceOptions) {
        if (sourceFilter != null && sourceOptions.none { it.first == sourceFilter }) {
            sourceFilter = null
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showReplacementPicker by remember { mutableStateOf(false) }
    var slots by remember(accountId) { mutableStateOf(HomeWidgetPlacement.currentSlots()) }
    val revision by HomeWidgetPlacement.revision.collectAsState()

    LaunchedEffect(accountId) {
        if (!accountId.isNullOrBlank()) {
            CampusNoticeRepository.open(accountId)
            CampusNoticeRepository.requestSyncIfDue()
        }
    }
    LaunchedEffect(revision, accountId) { slots = HomeWidgetPlacement.currentSlots() }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !accountId.isNullOrBlank()) {
            CampusNoticeRepository.setNotificationsEnabled(accountId, true)
        }
    }

    fun openArticle(notice: CampusNotice) {
        if (accountId.isNullOrBlank() ||
            !CampusNoticeUrlPolicy.isTrustedArticle(notice.sourceId, notice.originalUrl)
        ) return
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notice.originalUrl)))
        }.isSuccess
        if (opened) CampusNoticeRepository.markRead(accountId, notice.articleId)
        else Toast.makeText(context, "无法打开公告链接", Toast.LENGTH_SHORT).show()
    }

    fun addToHome() {
        slots = HomeWidgetPlacement.currentSlots()
        if (HomeWidgetPlacement.NOTICE_WIDGET_ID in slots) return
        val empty = slots.indexOfFirst { it == null }
        if (empty < 0) {
            showSettings = false
            showReplacementPicker = true
        } else if (HomeWidgetPlacement.placeNoticeAt(empty)) {
            slots = HomeWidgetPlacement.currentSlots()
        }
    }

    fun openOfficialSite() {
        val url = when (sourceFilter) {
            AcademicAffairsNoticeParser.SOURCE_ID -> AcademicAffairsNoticeParser.FIRST_PAGE
            PostgraduateNoticeParser.SOURCE_ID -> PostgraduateNoticeParser.FIRST_PAGE
            UniversityNoticeParser.SOURCE_ID -> UniversityNoticeParser.FIRST_PAGE
            CollegeNoticeRegistry.SOURCE_ID -> CollegeNoticeRegistry.resolveCurrent()?.listUrl
            else -> if (graduate) PostgraduateNoticeParser.FIRST_PAGE
            else UniversityNoticeParser.FIRST_PAGE
        } ?: UniversityNoticeParser.FIRST_PAGE
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!opened) Toast.makeText(context, "无法打开官方网站", Toast.LENGTH_SHORT).show()
    }

    AppPageScaffold(
        title = "校园通知",
        modifier = Modifier.fillMaxSize(),
        actions = listOf(
            TrailingAction(Icons.Outlined.OpenInBrowser, "在浏览器查看") { openOfficialSite() },
            TrailingAction(Icons.Outlined.Settings, "校园通知设置") { showSettings = true }
        ),
        trailingContent = {
            NoticeSourceFilter(
                options = sourceOptions,
                selected = sourceFilter,
                onSelect = { sourceFilter = it }
            )
        },
        freeContent = {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { CampusNoticeRepository.requestSyncIfDue(force = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (notices.isEmpty()) {
                        item(key = "state") {
                            val error = if (sourceFilter == null) {
                                snapshot?.sourceStatuses?.values?.any { it.lastError != null } == true
                            } else {
                                snapshot?.sourceStatuses?.get(sourceFilter)?.lastError != null
                            }
                            when {
                                accountId.isNullOrBlank() -> AppStateCard.Empty("请先登录以查看公告")
                                isRefreshing || snapshot == null -> AppStateCard.Loading()
                                error -> AppStateCard.Error(message = "下拉刷新重试", title = "公告暂时无法加载")
                                else -> AppStateCard.Empty(
                                    if (sourceFilter == null) "暂无公告" else "该信源暂无公告"
                                )
                            }
                        }
                    } else {
                        items(notices, key = { "${it.sourceId}:${it.articleId}" }) { notice ->
                            CampusNoticeRow(notice = notice, onClick = { openArticle(notice) })
                        }
                        if (hasMore) {
                            item(key = "load_more") {
                                // 滑到列表底部时自动翻一页；失败后只保留手动重试，避免反复打 WAF。
                                LaunchedEffect(notices.size, sourceFilter, historyError) {
                                    if (!isLoadingMore && !historyError) {
                                        CampusNoticeRepository.requestLoadMore(sourceFilter)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMore) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppCircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                                            Text(
                                                "正在加载更早公告…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        TextButton(onClick = {
                                            CampusNoticeRepository.requestLoadMore(sourceFilter)
                                        }) {
                                            Text(if (historyError) "加载失败，点击重试" else "加载更早公告")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (showSettings) {
        AppModalBottomSheet(
            title = "校园通知设置",
            onDismissRequest = { showSettings = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("新公告通知", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "打开应用检测到新公告时提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppToggle(
                        checked = snapshot?.notificationsEnabled == true,
                        onCheckedChange = { enabled ->
                            if (accountId.isNullOrBlank()) return@AppToggle
                            if (!enabled) {
                                CampusNoticeRepository.setNotificationsEnabled(accountId, false)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                CampusNoticeRepository.setNotificationsEnabled(accountId, true)
                            }
                        },
                        enabled = !accountId.isNullOrBlank(),
                        contentDescription = "新公告通知"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("在首页显示", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "在首页小工具中显示校园通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppToggle(
                        checked = HomeWidgetPlacement.NOTICE_WIDGET_ID in slots,
                        onCheckedChange = { enabled ->
                            if (enabled) addToHome()
                            else if (HomeWidgetPlacement.removeNotice()) {
                                slots = HomeWidgetPlacement.currentSlots()
                            }
                        },
                        contentDescription = "在首页显示校园通知"
                    )
                }

                if ((snapshot?.unreadCount ?: 0) > 0 && !accountId.isNullOrBlank()) {
                    TextButton(onClick = { CampusNoticeRepository.markRead(accountId, null) }) {
                        Text("全部标为已读")
                    }
                }
                Text(
                    "未读公告超过 7 天后自动标为已读",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showReplacementPicker) {
        AlertDialog(
            onDismissRequest = { showReplacementPicker = false },
            title = { Text("首页已放满") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("选择要替换的小工具")
                    slots.forEachIndexed { index, id ->
                        TextButton(onClick = {
                            if (HomeWidgetPlacement.placeNoticeAt(index)) {
                                slots = HomeWidgetPlacement.currentSlots()
                                Toast.makeText(context, "已添加到首页", Toast.LENGTH_SHORT).show()
                            }
                            showReplacementPicker = false
                        }) {
                            Text("${index + 1}. ${HomeWidgetRegistry.widgetById[id]?.title ?: "空位"}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReplacementPicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun NoticeSourceFilter(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: "全部"
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "筛选信源",
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (sourceId, sourceLabel) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sourceLabel,
                            fontWeight = if (sourceId == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (sourceId == selected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(sourceId)
                    }
                )
            }
        }
    }
}

@Composable
private fun CampusNoticeRow(notice: CampusNotice, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = SmoothRoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = notice.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (notice.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!notice.read) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "未读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Text(
                "${noticeSourceLabel(notice.sourceId)} · ${notice.publishedOn}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
