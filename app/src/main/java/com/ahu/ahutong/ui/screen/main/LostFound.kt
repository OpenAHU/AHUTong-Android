package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppFloatingActionButton
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppSearchField
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.LostFoundViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.icon.icons.useful.Cancel
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Search
import com.ahu.ahutong.core.designsystem.R as DesignSystemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostFound(
    onBack: (() -> Unit)? = null,
    lostFoundViewModel: LostFoundViewModel = hiltViewModel()
) {
    DisposableEffect(lostFoundViewModel) {
        onDispose { lostFoundViewModel.onPresetSurfaceDisposed() }
    }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    val allCampus =
        lostFoundViewModel.allCampus?.`object`.orEmpty()

    val allLostFoundType =
        lostFoundViewModel.allLostFoundType?.`object`.orEmpty()

    val lostFoundList =
        lostFoundViewModel.lostFoundList

    var searchExpanded by rememberSaveable {
        mutableStateOf(false)
    }

    var showPublishSheet by remember {
        mutableStateOf(false)
    }
    var showMyPostSheet by remember {
        mutableStateOf(false)
    }
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var selectedItem by remember {
        mutableStateOf<LostFoundItem?>(null)
    }

    var showImageViewer by remember {
        mutableStateOf(false)
    }

    var imageViewerIndex by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            lostFoundViewModel.refreshList()
        }
    }

    /**
     * 高亮匹配文本
     */
    @Composable
    fun highlightText(
        text: String,
        keyword: String
    ) = buildAnnotatedString {
        if (keyword.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }

        val lowerText = text.lowercase()
        val lowerKeyword = keyword.lowercase()

        var startIndex = 0

        while (true) {
            val matchIndex =
                lowerText.indexOf(
                    lowerKeyword,
                    startIndex
                )

            if (matchIndex == -1) {
                append(
                    text.substring(startIndex)
                )
                break
            }

            append(
                text.substring(
                    startIndex,
                    matchIndex
                )
            )

            pushStyle(
                SpanStyle(
                    background = 90.a1,
                    fontWeight = FontWeight.Bold
                )
            )

            append(
                text.substring(
                    matchIndex,
                    matchIndex + keyword.length
                )
            )

            pop()

            startIndex =
                matchIndex + keyword.length
        }
    }

    /**
     * 搜索 + 筛选
     */
    val filteredList = lostFoundList.distinctBy(LostFoundItem::id).filter { item ->
        val campusMatch =
            lostFoundViewModel.selectedCampus == null ||
                    item.campusid == lostFoundViewModel.selectedCampus

        val typeMatch =
            lostFoundViewModel.selectedType == null ||
                    item.typeid == lostFoundViewModel.selectedType

        val searchMatch =
            searchQuery.isBlank() ||

                    (item.title?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.linkman?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.phone?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.campusName?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.lostType?.typeName?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.pubuser?.userName?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.num1?.contains(
                        searchQuery,
                        true
                    ) == true) ||

                    (item.createtime?.contains(
                        searchQuery,
                        true
                    ) == true)

        campusMatch && typeMatch && searchMatch
    }

    /**
     * 自动加载更多
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo
                .visibleItemsInfo
                .lastOrNull()
                ?.index
        }
            .distinctUntilChanged()
            .collect { lastVisibleItem ->
                val totalItems =
                    listState.layoutInfo.totalItemsCount

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

    val pageContent: LazyListScope.() -> Unit = {

            if (searchExpanded) item {
                AppSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = "搜索全部信息"
                )
            }

            if (!searchExpanded) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppSelectField(
                            label = "信息类别",
                            selected = lostFoundViewModel.currentState,
                            options = listOf(
                                AppSelectOption(1, "失物招领"),
                                AppSelectOption(2, "寻物启事")
                            ),
                            onSelected = lostFoundViewModel::switchState,
                            miuixStandalone = true
                        )
                        AppSelectField(
                            label = "校区",
                            selected = lostFoundViewModel.selectedCampus,
                            options = listOf(AppSelectOption<String?>(null, "全部校区")) +
                                allCampus.map { campus ->
                                    AppSelectOption<String?>(campus.id, campus.campusName)
                                },
                            onSelected = lostFoundViewModel::selectCampusFilter,
                            miuixStandalone = true
                        )
                        AppSelectField(
                            label = "物品类型",
                            selected = lostFoundViewModel.selectedType,
                            options = listOf(AppSelectOption<String?>(null, "全部类型")) +
                                allLostFoundType.map { type ->
                                    AppSelectOption<String?>(type.typeId, type.typeName)
                                },
                            onSelected = lostFoundViewModel::selectTypeFilter,
                            miuixStandalone = true
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text =
                            if (
                                searchExpanded &&
                                searchQuery.isNotBlank()
                            ) {
                                "搜索「$searchQuery」到 ${filteredList.size} 条记录"
                            } else {
                                "共 ${filteredList.size} 条记录"
                            },
                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    TextButton(
                        onClick = {
                            showMyPostSheet = true
                            lostFoundViewModel.loadMyPosts()
                        }
                    ) {
                        Text("管理我的帖子")
                    }
                }
            }

            if (lostFoundViewModel.listLoading && lostFoundList.isEmpty()) {
                item {
                    AppStateCard.Loading()
                }
            }

            lostFoundViewModel.errorMessage?.let { message ->
                item {
                    AppStateCard.InlineError(
                        message = message,
                        onRetry = { lostFoundViewModel.fetchFirstPage() }
                    )
                }
            }

            if (!lostFoundViewModel.listLoading && filteredList.isEmpty() && lostFoundViewModel.errorMessage == null) {
                item {
                    AppStateCard.Inline(
                        title = "暂无匹配内容",
                        message = "尝试切换校区、类型或清空搜索关键词。"
                    )
                }
            }

            items(filteredList, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .padding(
                            horizontal = 16.dp
                        )
                        .fillMaxWidth()
                        .appLiquidGlassSurface(
                            shape = SmoothRoundedCornerShape(20.dp),
                            fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                            level = LiquidGlassSurfaceLevel.Panel
                        )
                        .clickable {
                            selectedItem = item
                        }
                        .padding(24.dp, 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text =
                            highlightText(
                                item.title ?: "无标题",
                                searchQuery
                            ),
                        fontWeight =
                            FontWeight.Bold,
                        style =
                            MaterialTheme.typography
                                .titleMedium
                    )

                    Text(
                        buildAnnotatedString {
                            append("联系人：")
                            append(
                                highlightText(
                                    item.linkman ?: "未知",
                                    searchQuery
                                )
                            )
                        }
                    )

                    Text(
                        buildAnnotatedString {
                            append("联系电话：")
                            append(
                                highlightText(
                                    item.phone ?: "未知",
                                    searchQuery
                                )
                            )
                        }
                    )

                    Text(
                        buildAnnotatedString {
                            append("校区：")
                            append(
                                highlightText(
                                    item.campusName ?: "未知",
                                    searchQuery
                                )
                            )
                        }
                    )

                    Text(
                        buildAnnotatedString {
                            append("类型：")
                            append(
                                highlightText(
                                    item.lostType?.typeName
                                        ?: "未知",
                                    searchQuery
                                )
                            )
                        }
                    )

                    Text(
                        buildAnnotatedString {
                            append("证件号：")
                            append(
                                highlightText(
                                    item.num1?:"未知",
                                    searchQuery
                                )
                            )
                        }
                    )

                    Text(
                        text =
                            item.createtime
                                ?: "未知时间",
                        color =
                            50.n1 withNight 80.n1
                    )
                }
            }
            if (lostFoundViewModel.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        AppCircularProgressIndicator()
                    }
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
    ) {
        AppPageScaffold(
            title = "失物招领",
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
            listState = listState,
            trailingContent = {
                AppTitleIconButton(
                    icon = DesignSystemR.drawable.ic_refresh,
                    contentDescription = "刷新失物招领",
                    onClick = lostFoundViewModel::refreshList
                )
                AppTitleIconButton(
                    icon = if (searchExpanded) null else DesignSystemR.drawable.ic_find,
                    imageVector = if (searchExpanded) Icons.Default.Close else null,
                    contentDescription = if (searchExpanded) "关闭搜索" else "搜索",
                    onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }
                )
            },
            bottomPadding = 96.dp,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            lazyContent = pageContent
        )
        AppFloatingActionButton(
            onClick = {
                showPublishSheet = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            val addColor = LocalContentColor.current
            Canvas(modifier = Modifier.size(22.dp)) {
                val strokeWidth = 2.5.dp.toPx()
                drawLine(
                    color = addColor,
                    start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = addColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        selectedItem?.let { item ->
            AppModalBottomSheet(
                title = item.title ?: "无标题",
                onDismissRequest = {
                    selectedItem = null
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "联系人：${item.linkman ?: "未知"}"
                    )

                    Text(
                        "联系电话：${item.phone ?: "未知"}"
                    )

                    Text(
                        "校区：${item.campusName ?: "未知"}"
                    )

                    Text(
                        "类型：${item.lostType?.typeName ?: "未知"}"
                    )

                    Text(
                        "发布时间：${item.createtime ?: "未知"}"
                    )
                    Text(
                        "证件号：${item.num1 ?: "未知"}"
                    )

                    if (item.imgs.isNotEmpty()) {
                        Text(
                            text = "相关图片",
                            style =
                                MaterialTheme.typography
                                    .titleMedium
                        )

                        LazyRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    12.dp
                                )
                        ) {
                            items(item.imgs) { img ->
                                     val imgIndex = item.imgs.indexOf(img)
                                     Card(
                                         modifier =
                                             Modifier
                                                 .size(
                                                     180.dp
                                                 )
                                                 .clickable {
                                                     imageViewerIndex = imgIndex
                                                     showImageViewer = true
                                                 }
                                     ) {
                                         AsyncImage(
                                             model =
                                                 "https://adwmh.ahu.edu.cn${img.imgPath}",
                                             contentDescription =
                                                 null,
                                             modifier =
                                                 Modifier.fillMaxSize()
                                         )
                                     }
                                 }
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(24.dp)
                    )
                }
            }
        }
    }

    /**
     * 全屏图片查看器
     */
    if (showImageViewer) {
        val imgs = selectedItem?.imgs.orEmpty()
        val pagerState = rememberPagerState(
            initialPage = imageViewerIndex,
            pageCount = { imgs.size }
        )

        Dialog(
            onDismissRequest = { showImageViewer = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://adwmh.ahu.edu.cn${imgs[page].imgPath}",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 关闭按钮
                IconButton(
                    onClick = { showImageViewer = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .statusBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }

                // 页码指示器
                if (imgs.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${imgs.size}",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                    )
                }
            }
        }
    }

    if (showMyPostSheet) {
        AppModalBottomSheet(
            title = "管理我的帖子",
            onDismissRequest = {
                showMyPostSheet = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                when {
                    lostFoundViewModel.myPostsLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppCircularProgressIndicator(size = 24.dp, strokeWidth = 2.5.dp)
                    }
                    lostFoundViewModel.myPostsError != null -> Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = lostFoundViewModel.myPostsError.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                        AppButton(
                            onClick = lostFoundViewModel::loadMyPosts,
                            modifier = Modifier.fillMaxWidth(),
                            variant = AppButtonVariant.Secondary
                        ) { Text("重试") }
                    }
                    lostFoundViewModel.myPosts.isEmpty() -> Text("暂无帖子")
                    else -> {
                    LazyColumn(
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = lostFoundViewModel.myPosts,
                            key = LostFoundItem::id
                        ) { item ->
                            Card(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text =
                                                item.title
                                                    ?: "无标题",
                                            fontWeight =
                                                FontWeight.Bold
                                        )

                                        Text(
                                            text =
                                                item.createtime
                                                    ?: ""
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            lostFoundViewModel.deleteLostFound(item.id) { result ->
                                                Toast.makeText(
                                                    context,
                                                    if (result.isSuccess) "删除成功" else
                                                        result.exceptionOrNull()?.message ?: "删除失败",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        enabled = item.id !in lostFoundViewModel.deletingPostIds
                                    ) {
                                        if (item.id in lostFoundViewModel.deletingPostIds) {
                                            AppCircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
                                        } else {
                                            Text("删除")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    /**
     * 发帖 BottomSheet
     */
    if (showPublishSheet) {

        var linkman by rememberSaveable {
            mutableStateOf("")
        }

        var phone by rememberSaveable {
            mutableStateOf("")
        }

        var title by rememberSaveable {
            mutableStateOf("")
        }

        var num1 by rememberSaveable {
            mutableStateOf("")
        }

        var publishCampusId by rememberSaveable {
            mutableStateOf<String?>(null)
        }

        var publishTypeId by rememberSaveable {
            mutableStateOf<String?>(null)
        }

        var publishState by rememberSaveable {
            mutableStateOf("1")
        }

        AppModalBottomSheet(
            title = "发布帖子",
            onDismissRequest = {
                showPublishSheet = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ){
                Text(
                     text = "*目前智慧安大图片功能有时无法使用，请大家文字描述尽量详尽",
                     modifier = Modifier.padding(16.dp),
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                     fontSize = 14.sp
                 )
                AppTextField(
                    value = linkman,
                    onValueChange = { linkman = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "联系人 *"
                )

                AppTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "联系电话 *"
                )

                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "描述内容 *"
                )

                AppTextField(
                    value = num1,
                    onValueChange = { num1 = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "证件号（可选）"
                )

                AppSelectField(
                    label = "校区 *",
                    selected = publishCampusId,
                    options = allCampus.map { campus ->
                        AppSelectOption(campus.id, campus.campusName)
                    },
                    onSelected = { publishCampusId = it },
                    placeholder = "请选择校区",
                    miuixStandalone = true
                )

                AppSelectField(
                    label = "物品类型 *",
                    selected = publishTypeId,
                    options = allLostFoundType.map { type ->
                        AppSelectOption(type.typeId, type.typeName)
                    },
                    onSelected = { publishTypeId = it },
                    placeholder = "请选择物品类型",
                    miuixStandalone = true
                )

                AppSelectField(
                    label = "信息类别 *",
                    selected = publishState,
                    options = listOf(
                        AppSelectOption("1", "失物招领"),
                        AppSelectOption("2", "寻物启事")
                    ),
                    onSelected = { publishState = it },
                    miuixStandalone = true
                )

                AppButton(
                    onClick = {

                        if (
                            linkman.isBlank() ||
                            phone.isBlank() ||
                            title.isBlank() ||
                            publishCampusId == null ||
                            publishTypeId == null
                        ) {
                            Toast.makeText(
                                context,
                                "请填写完整信息",
                                Toast.LENGTH_SHORT
                            ).show()

                            return@AppButton
                        }

                        lostFoundViewModel.publishLostFound(
                            linkman = linkman,
                            phone = phone,
                            title = title,
                            num1 = num1,
                            campusId = publishCampusId!!,
                            typeId = publishTypeId!!,
                            state = publishState
                        ) { result ->
                            if (result.isSuccess) showPublishSheet = false
                            Toast.makeText(
                                context,
                                if (result.isSuccess) "发布成功" else
                                    result.exceptionOrNull()?.message ?: "发布失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !lostFoundViewModel.isPublishing
                ) {
                    if (lostFoundViewModel.isPublishing) {
                        AppCircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (lostFoundViewModel.isPublishing) "正在发布" else "发布")
                }

                Spacer(
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}
