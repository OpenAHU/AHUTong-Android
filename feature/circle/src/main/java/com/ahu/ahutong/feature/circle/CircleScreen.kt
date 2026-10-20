package com.ahu.ahutong.feature.circle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 列表/详情/热榜的内部页面态：插件自建导航，不占宿主路由。 */
private sealed interface CirclePage {
    data object Feed : CirclePage
    data object Hot : CirclePage
    data class Detail(val topic: CircleTopic, val from: DetailFrom) : CirclePage
}

private enum class DetailFrom { Feed, Hot }

/** 热榜内存缓存（10 分钟 TTL，遵守「热榜口低频访问」纪律）。 */
private var hotCache: Pair<Long, List<CircleTopic>>? = null
private const val HOT_CACHE_TTL_MS = 10 * 60 * 1000L

@Composable
internal fun CircleRoot(host: com.ahu.ahutong.core.plugin.PluginHostServices) {
    val api = remember { CircleApi(host.http()) }
    var page by remember { mutableStateOf<CirclePage>(CirclePage.Feed) }

    // 宿主把 Entry 全屏渲染（edge-to-edge、无脚手架），系统栏避让是插件自己的责任
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        when (val current = page) {
            CirclePage.Feed -> CircleFeedPage(
                api = api,
                onOpen = { page = CirclePage.Detail(it, DetailFrom.Feed) },
                onOpenHot = { page = CirclePage.Hot }
            )
            CirclePage.Hot -> CircleHotPage(
                api = api,
                onOpen = { page = CirclePage.Detail(it, DetailFrom.Hot) },
                onBack = { page = CirclePage.Feed }
            )
            is CirclePage.Detail -> CircleDetailPage(
                api = api,
                topic = current.topic,
                onBack = {
                    page = if (current.from == DetailFrom.Hot) CirclePage.Hot else CirclePage.Feed
                }
            )
        }
    }
}

/* ==================== 信息流页 ==================== */

@Composable
private fun CircleFeedPage(api: CircleApi, onOpen: (CircleTopic) -> Unit, onOpenHot: () -> Unit) {
    var topics by remember { mutableStateOf<List<CircleTopic>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }
    var sourceReady by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val detailSemaphore = remember { Semaphore(6) }

    suspend fun loadPage(reset: Boolean) {
        try {
            val index = api.feed(if (reset) null else nextCursor)
            nextCursor = index.nextCursor
            hasMore = index.hasMore
            sourceReady = index.isReady
            // 只增量解析新 id 的正文（已缓存的 id 直接命中 LRU）
            val fetched = coroutineScope {
                index.ids.map { id ->
                    async(Dispatchers.IO) {
                        detailSemaphore.withPermit {
                            runCatching { api.fetchTopicDetail(id) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }.filterNotNull()
            val base = if (reset) emptyList() else topics
            topics = base + fetched.filter { t -> base.none { it.id == t.id } }
            error = null
        } catch (e: Exception) {
            if (reset || topics.isEmpty()) error = e.message ?: "加载失败"
        }
    }

    // 初次加载与手动刷新/重试共用同一触发器
    LaunchedEffect(reloadTrigger) {
        loading = true
        loadPage(reset = true)
        loading = false
    }

    // 滚到底自动翻页
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && !loadingMore && !loading && error == null && info.totalItemsCount > 0 &&
                last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadingMore = true
            loadPage(reset = false)
            loadingMore = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CircleHeader(title = "校园圈子", onBack = null) {
            IconButton(onClick = onOpenHot) {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = "十大热帖")
            }
            IconButton(onClick = {
                topics = emptyList()
                nextCursor = null
                hasMore = true
                reloadTrigger++
            }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
            }
        }

        // 第三方内容免责条
        Text(
            text = "内容为第三方社区用户发布，不代表本应用立场 · 只读浏览",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (!sourceReady) {
            Text(
                "上游索引同步中，内容可能不完整",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        when {
            loading && topics.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { AppCircularProgressIndicator() }

            error != null && topics.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text("圈子服务暂不可用", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    error.orEmpty(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppButton(
                    onClick = { reloadTrigger++ },
                    variant = AppButtonVariant.Secondary
                ) { Text("重试") }
                Spacer(Modifier.weight(1f))
            }

            topics.isEmpty() && !loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("这个时段还没有新帖子", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                AppButton(
                    onClick = { reloadTrigger++ },
                    variant = AppButtonVariant.Secondary
                ) { Text("刷新") }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(topics, key = { it.id }) { topic ->
                    TopicCard(topic = topic, onClick = { onOpen(topic) })
                }
                if (loadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) { AppCircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp) }
                    }
                }
                if (!hasMore && topics.isNotEmpty()) {
                    item {
                        Text(
                            "到底啦",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/* ==================== 热榜页 ==================== */

@Composable
private fun CircleHotPage(api: CircleApi, onOpen: (CircleTopic) -> Unit, onBack: () -> Unit) {
    var hot by remember { mutableStateOf<List<CircleTopic>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        // 首载走内存缓存（10 分钟），刷新/重试强制回源
        val cached = hotCache
        if (reloadTrigger == 0 && cached != null &&
            System.currentTimeMillis() - cached.first < HOT_CACHE_TTL_MS
        ) {
            hot = cached.second
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val list = api.fetchHotTopics()
            hot = list
            hotCache = System.currentTimeMillis() to list
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CircleHeader(title = "十大热帖", onBack = onBack) {
            IconButton(onClick = {
                hotCache = null
                reloadTrigger++
            }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
            }
        }
        Text(
            text = "内容为第三方社区用户发布，不代表本应用立场 · 热度由官方榜单提供",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        when {
            loading && hot.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { AppCircularProgressIndicator() }

            error != null && hot.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text("热榜暂不可用", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    error.orEmpty(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppButton(
                    onClick = {
                        hotCache = null
                        reloadTrigger++
                    },
                    variant = AppButtonVariant.Secondary
                ) { Text("重试") }
                Spacer(Modifier.weight(1f))
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(hot, key = { it.id }) { topic ->
                    TopicCard(topic = topic, onClick = { onOpen(topic) }, showRank = true)
                }
            }
        }
    }
}

/* ==================== 详情页 ==================== */

@Composable
private fun CircleDetailPage(api: CircleApi, topic: CircleTopic, onBack: () -> Unit) {
    var detail by remember { mutableStateOf(topic) }
    var comments by remember { mutableStateOf<List<CircleComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var commentPage by remember { mutableIntStateOf(1) }
    var loadingComments by remember { mutableStateOf(false) }
    var hasMoreComments by remember { mutableStateOf(false) }

    LaunchedEffect(topic.id) {
        detail = runCatching { api.fetchTopicDetail(topic.id) }.getOrDefault(topic)
        val first = runCatching { api.fetchComments(topic.id, 1) }.getOrDefault(emptyList())
        comments = first
        hasMoreComments = first.size >= CircleApi.COMMENT_PAGE_SIZE
        loading = false
    }

    // 加载更多评论（page 递增；服务端 pageSize 固定 6）
    LaunchedEffect(loadingComments) {
        if (!loadingComments) return@LaunchedEffect
        val next = commentPage + 1
        val more = runCatching { api.fetchComments(topic.id, next) }.getOrDefault(emptyList())
        if (more.isNotEmpty()) {
            comments = comments + more.filter { c -> comments.none { it.id == c.id } }
            commentPage = next
        }
        hasMoreComments = more.size >= CircleApi.COMMENT_PAGE_SIZE
        loadingComments = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CircleHeader(title = detail.title.ifBlank { "帖子详情" }, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AppCard {
                    Text(
                        "${if (detail.isAnon) "匿名" else detail.authorName} · ${detail.createTime.take(16)}" +
                            if (detail.nodeName.isNotBlank()) " · ${detail.nodeName}" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(detail.content, fontSize = 14.sp)
                    detail.imageUrls.forEach { url ->
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                Text(
                    "评论 ${comments.size}${if (hasMoreComments) "+" else ""}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        AppCircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp)
                    }
                }
            }
            items(comments, key = { it.id }) { comment ->
                AppCard {
                    Text(
                        "${comment.authorName} · ${comment.createTime.take(16)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(comment.content, fontSize = 13.sp)
                }
            }
            if (hasMoreComments) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppButton(
                            onClick = { loadingComments = true },
                            enabled = !loadingComments,
                            variant = AppButtonVariant.Secondary
                        ) {
                            if (loadingComments) {
                                AppCircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (loadingComments) "加载中…" else "加载更多评论")
                        }
                    }
                }
            }
        }
    }
}

/* ==================== 公共组件 ==================== */

@Composable
private fun TopicCard(topic: CircleTopic, onClick: () -> Unit, showRank: Boolean = false) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showRank) {
                Text("🔥", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
            }
            if (topic.isTop) {
                Text(
                    "置顶",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
            }
            if (topic.nodeName.isNotBlank()) {
                Text(
                    topic.nodeName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                topic.createTime.take(10),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (topic.title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                topic.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            topic.content,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "💬 ${topic.commentCount}   👍 ${topic.likeCount}   👀 ${topic.viewCount}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CircleHeader(
    title: String,
    onBack: (() -> Unit)?,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}