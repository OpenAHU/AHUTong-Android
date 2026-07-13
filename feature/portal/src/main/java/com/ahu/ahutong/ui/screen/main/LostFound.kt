package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.ui.components.AhuBottomSheet
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.components.AhuChip
import com.ahu.ahutong.ui.components.AhuFab
import com.ahu.ahutong.ui.components.AhuFilterBar
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuHighlightText
import com.ahu.ahutong.ui.components.AhuIconActionGroup
import com.ahu.ahutong.ui.components.AhuImageViewer
import com.ahu.ahutong.ui.components.AhuLoadingMore
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreenBox
import com.ahu.ahutong.ui.components.AhuSearchField
import com.ahu.ahutong.ui.components.AhuSegmentedTabs
import com.ahu.ahutong.ui.components.AhuTextButton
import com.ahu.ahutong.ui.components.AhuTextField
import com.ahu.ahutong.ui.components.ahuFabPadding
import com.ahu.ahutong.ui.components.ahuHighlightAnnotated
import com.ahu.ahutong.ui.state.LostFoundViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import kotlinx.coroutines.flow.distinctUntilChanged

private const val IMAGE_HOST = "https://adwmh.ahu.edu.cn"

/**
 * 失物招领 / 寻物启事
 *
 * UI 意图：
 * 1. 顶部分段切换两种业务态（state=1 失物招领 / state=2 寻物启事）
 * 2. 右侧胶囊操作组：刷新 + 展开搜索
 * 3. 校区 / 类型横向筛选条（胶囊底 + Chip）
 * 4. 无限滚动列表卡片，搜索关键字高亮
 * 5. FAB 发帖；点卡片出详情 BottomSheet（含图廊）
 * 6. 管理我的帖子 / 全屏看图
 *
 * 视觉一律走 designsystem（Ahu*），业务逻辑在 [LostFoundViewModel]。
 */
@Composable
fun LostFound(
    lostFoundViewModel: LostFoundViewModel = hiltViewModel(),
    mockRefreshRevision: Long = 0L,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val allCampus = lostFoundViewModel.allCampus?.`object`.orEmpty()
    val allLostFoundType = lostFoundViewModel.allLostFoundType?.`object`.orEmpty()
    val lostFoundList = lostFoundViewModel.lostFoundList

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCampus by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }

    var showPublishSheet by remember { mutableStateOf(false) }
    var showMyPostSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<LostFoundItem?>(null) }
    var showImageViewer by remember { mutableStateOf(false) }
    var imageViewerIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && lostFoundViewModel.isMockMode()) {
            lostFoundViewModel.refreshList()
        }
    }

    val filteredList = remember(
        lostFoundList,
        selectedCampus,
        selectedType,
        searchQuery,
    ) {
        lostFoundList.filter { item ->
            val campusMatch = selectedCampus == null || item.campusid == selectedCampus
            val typeMatch = selectedType == null || item.typeid == selectedType
            val q = searchQuery
            val searchMatch = q.isBlank() ||
                item.title?.contains(q, true) == true ||
                item.linkman?.contains(q, true) == true ||
                item.phone?.contains(q, true) == true ||
                item.campusName?.contains(q, true) == true ||
                item.lostType?.typeName?.contains(q, true) == true ||
                item.pubuser?.userName?.contains(q, true) == true ||
                item.num1?.contains(q, true) == true ||
                item.createtime?.contains(q, true) == true
            campusMatch && typeMatch && searchMatch
        }
    }

    // 滑到列表底部自动分页
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .distinctUntilChanged()
            .collect { lastVisibleItem ->
                val totalItems = listState.layoutInfo.totalItemsCount
                if (
                    lastVisibleItem != null &&
                    lastVisibleItem == totalItems - 1 &&
                    !lostFoundViewModel.listLoading &&
                    !lostFoundViewModel.isLoadingMore &&
                    lostFoundViewModel.hasMore
                ) {
                    lostFoundViewModel.loadMore()
                }
            }
    }

    AhuScreenBox {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AhuDimens.SectionSpacing),
            contentPadding = PaddingValues(bottom = AhuDimens.BottomNavClearance),
        ) {
            // ── 顶栏：分段 + 操作 ──────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AhuDimens.TitleHorizontal,
                            top = 24.dp,
                            end = AhuDimens.TitleHorizontal,
                            bottom = 8.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AhuSegmentedTabs(
                            options = listOf("失物招领", "寻物启事"),
                            selectedIndex = (lostFoundViewModel.currentState - 1).coerceIn(0, 1),
                            onSelect = { lostFoundViewModel.switchState(it + 1) },
                            modifier = Modifier.weight(1f),
                        )
                        AhuIconActionGroup {
                            AhuHeaderIconButton(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                onClick = {
                                    lostFoundViewModel.refreshList()
                                    Toast.makeText(context, "刷新成功", Toast.LENGTH_SHORT).show()
                                },
                            )
                            AhuHeaderIconButton(
                                imageVector = if (searchExpanded) {
                                    Icons.Default.Close
                                } else {
                                    Icons.Default.Search
                                },
                                contentDescription = if (searchExpanded) "关闭搜索" else "搜索",
                                onClick = {
                                    searchExpanded = !searchExpanded
                                    if (!searchExpanded) searchQuery = ""
                                },
                            )
                        }
                    }

                    if (searchExpanded) {
                        AhuSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "搜索全部信息",
                        )
                    }
                }
            }

            // ── 筛选条（搜索展开时隐藏，避免信息过载）────────────────────
            if (!searchExpanded) {
                item {
                    AhuFilterBar(scrollable = true) {
                        AhuChip(
                            text = "全部校区",
                            selected = selectedCampus == null,
                            onClick = { selectedCampus = null },
                        )
                        allCampus.forEach { campus ->
                            AhuChip(
                                text = campus.campusName,
                                selected = selectedCampus == campus.id,
                                onClick = { selectedCampus = campus.id },
                            )
                        }
                    }
                }
                item {
                    AhuFilterBar(scrollable = true) {
                        AhuChip(
                            text = "全部类型",
                            selected = selectedType == null,
                            onClick = { selectedType = null },
                        )
                        allLostFoundType.forEach { type ->
                            AhuChip(
                                text = type.typeName,
                                selected = selectedType == type.typeId,
                                onClick = { selectedType = type.typeId },
                            )
                        }
                    }
                }
            }

            // ── 结果计数 + 管理入口 ───────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AhuDimens.TitleHorizontal),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (searchExpanded && searchQuery.isNotBlank()) {
                            "搜索「$searchQuery」到 ${filteredList.size} 条记录"
                        } else {
                            "共 ${filteredList.size} 条记录"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = AhuColors.onSurface,
                    )
                    AhuTextButton(
                        text = "管理我的帖子",
                        onClick = { showMyPostSheet = true },
                    )
                }
            }

            // ── 列表卡片 ──────────────────────────────────────────────────
            items(filteredList, key = { it.id ?: it.hashCode() }) { item ->
                LostFoundListCard(
                    item = item,
                    keyword = searchQuery,
                    onClick = { selectedItem = item },
                )
            }

            if (lostFoundViewModel.isLoadingMore) {
                item { AhuLoadingMore() }
            }
        }

        AhuFab(
            onClick = { showPublishSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .ahuFabPadding(),
        )
    }

    // ── 详情 ──────────────────────────────────────────────────────────────
    selectedItem?.let { item ->
        AhuBottomSheet(
            onDismissRequest = { selectedItem = null },
            title = item.title ?: "无标题",
        ) {
            Text("联系人：${item.linkman ?: "未知"}", color = AhuColors.onSurface)
            Text("联系电话：${item.phone ?: "未知"}", color = AhuColors.onSurface)
            Text("校区：${item.campusName ?: "未知"}", color = AhuColors.onSurface)
            Text("类型：${item.lostType?.typeName ?: "未知"}", color = AhuColors.onSurface)
            Text("发布时间：${item.createtime ?: "未知"}", color = AhuColors.onSurface)
            Text("证件号：${item.num1 ?: "未知"}", color = AhuColors.onSurface)

            if (item.imgs.isNotEmpty()) {
                Text(
                    text = "相关图片",
                    style = MaterialTheme.typography.titleMedium,
                    color = AhuColors.onSurface,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(item.imgs.size) { imgIndex ->
                        val img = item.imgs[imgIndex]
                        AhuCard(
                            modifier = Modifier.size(180.dp),
                            cornerRadius = AhuDimens.CardCornerMedium,
                            contentPadding = PaddingValues(0.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            onClick = {
                                imageViewerIndex = imgIndex
                                showImageViewer = true
                            },
                        ) {
                            AsyncImage(
                                model = "$IMAGE_HOST${img.imgPath}",
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        imageViewerIndex = imgIndex
                                        showImageViewer = true
                                    },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 全屏看图 ──────────────────────────────────────────────────────────
    if (showImageViewer) {
        val urls = selectedItem?.imgs.orEmpty().map { "$IMAGE_HOST${it.imgPath}" }
        AhuImageViewer(
            imageUrls = urls,
            initialIndex = imageViewerIndex,
            onDismissRequest = { showImageViewer = false },
        )
    }

    // ── 管理我的帖子 ──────────────────────────────────────────────────────
    if (showMyPostSheet) {
        val myPosts = lostFoundList.filter {
            it.pubuser?.idNumber == lostFoundViewModel.currentUserName
        }
        AhuBottomSheet(
            onDismissRequest = { showMyPostSheet = false },
            title = "管理我的帖子",
        ) {
            if (myPosts.isEmpty()) {
                Text("暂无帖子", color = AhuColors.onSurface.copy(alpha = 0.6f))
            } else {
                myPosts.forEach { item ->
                    AhuCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title ?: "无标题",
                                    fontWeight = FontWeight.Bold,
                                    color = AhuColors.onSurface,
                                )
                                Text(
                                    text = item.createtime.orEmpty(),
                                    color = AhuColors.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            AhuTextButton(
                                text = "删除",
                                onClick = {
                                    item.id?.let { id ->
                                        lostFoundViewModel.deleteLostFound(id)
                                        Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 发帖 ──────────────────────────────────────────────────────────────
    if (showPublishSheet) {
        PublishLostFoundSheet(
            allCampus = allCampus,
            allTypes = allLostFoundType,
            onDismiss = { showPublishSheet = false },
            onPublish = { linkman, phone, title, num1, campusId, typeId, state ->
                lostFoundViewModel.publishLostFound(
                    linkman = linkman,
                    phone = phone,
                    title = title,
                    num1 = num1,
                    campusId = campusId,
                    typeId = typeId,
                    state = state,
                )
                showPublishSheet = false
                Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show()
            },
            onValidationError = {
                Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun LostFoundListCard(
    item: LostFoundItem,
    keyword: String,
    onClick: () -> Unit,
) {
    AhuCard(
        modifier = Modifier.padding(horizontal = AhuDimens.ContentHorizontal),
        cornerRadius = AhuDimens.ListItemCorner,
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AhuHighlightText(
            text = item.title ?: "无标题",
            keyword = keyword,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = AhuColors.onSurface,
        )
        HighlightedField(label = "联系人", value = item.linkman ?: "未知", keyword = keyword)
        HighlightedField(label = "联系电话", value = item.phone ?: "未知", keyword = keyword)
        HighlightedField(label = "校区", value = item.campusName ?: "未知", keyword = keyword)
        HighlightedField(
            label = "类型",
            value = item.lostType?.typeName ?: "未知",
            keyword = keyword,
        )
        HighlightedField(label = "证件号", value = item.num1 ?: "未知", keyword = keyword)
        Text(
            text = item.createtime ?: "未知时间",
            color = AhuColors.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun HighlightedField(
    label: String,
    value: String,
    keyword: String,
) {
    Text(
        text = buildAnnotatedString {
            append("$label：")
            append(ahuHighlightAnnotated(value, keyword))
        },
        color = AhuColors.onSurface,
    )
}

@Composable
private fun PublishLostFoundSheet(
    allCampus: List<com.ahu.ahutong.data.crawler.model.adwnh.CampusItem>,
    allTypes: List<com.ahu.ahutong.data.crawler.model.adwnh.LostFoundTypeItem>,
    onDismiss: () -> Unit,
    onPublish: (
        linkman: String,
        phone: String,
        title: String,
        num1: String,
        campusId: String,
        typeId: String,
        state: String,
    ) -> Unit,
    onValidationError: () -> Unit,
) {
    var linkman by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var num1 by rememberSaveable { mutableStateOf("") }
    var publishCampusId by rememberSaveable { mutableStateOf<String?>(null) }
    var publishTypeId by rememberSaveable { mutableStateOf<String?>(null) }
    var publishState by rememberSaveable { mutableStateOf("1") }

    AhuBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = true,
        title = "发布帖子",
    ) {
        Text(
            text = "*目前智慧安大图片功能有时无法使用，请大家文字描述尽量详尽",
            color = AhuColors.onSurface.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodyMedium,
        )

        AhuTextField(value = linkman, onValueChange = { linkman = it }, label = "联系人 *")
        AhuTextField(value = phone, onValueChange = { phone = it }, label = "联系电话 *")
        AhuTextField(value = title, onValueChange = { title = it }, label = "描述内容 *")
        AhuTextField(value = num1, onValueChange = { num1 = it }, label = "证件号（可选）")

        Text("选择校区", style = MaterialTheme.typography.titleSmall, color = AhuColors.onSurface)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allCampus) { campus ->
                AhuChip(
                    text = campus.campusName,
                    selected = publishCampusId == campus.id,
                    onClick = { publishCampusId = campus.id },
                )
            }
        }

        Text("选择类型", style = MaterialTheme.typography.titleSmall, color = AhuColors.onSurface)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allTypes) { type ->
                AhuChip(
                    text = type.typeName,
                    selected = publishTypeId == type.typeId,
                    onClick = { publishTypeId = type.typeId },
                )
            }
        }

        Text("选择事件类型", style = MaterialTheme.typography.titleSmall, color = AhuColors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AhuChip(
                text = "失物招领",
                selected = publishState == "1",
                onClick = { publishState = "1" },
            )
            AhuChip(
                text = "寻物启事",
                selected = publishState == "2",
                onClick = { publishState = "2" },
            )
        }

        AhuPrimaryButton(
            text = "发布",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (
                    linkman.isBlank() ||
                    phone.isBlank() ||
                    title.isBlank() ||
                    publishCampusId == null ||
                    publishTypeId == null
                ) {
                    onValidationError()
                    return@AhuPrimaryButton
                }
                onPublish(
                    linkman,
                    phone,
                    title,
                    num1,
                    publishCampusId!!,
                    publishTypeId!!,
                    publishState,
                )
            },
        )
    }
}
