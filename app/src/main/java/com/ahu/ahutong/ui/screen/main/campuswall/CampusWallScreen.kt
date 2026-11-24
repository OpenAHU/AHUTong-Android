package com.ahu.ahutong.ui.screen.main.campuswall

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ahu.ahutong.data.wall.WallComment
import com.ahu.ahutong.data.wall.WallPost
import com.ahu.ahutong.data.wall.WallTopic
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.TrailingAction
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
private const val PREVIEW_COMMENT_LIMIT = 3

/** 校园圈：只读时间线，排版参照朋友圈（头像 + 昵称 + 正文 + 九宫格 + 赞/评论）。 */
@Composable
fun CampusWallScreen(
    onBack: (() -> Unit)? = null,
    onOpenTopic: (Long) -> Unit = {},
    onOpenHot: () -> Unit = {},
) {
    val viewModel: CampusWallViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsState()
    val openImage = rememberWallImageOpener()
    AppPageScaffold(
        title = "逛逛校园圈",
        subtitle = "只读展示第三方平台内容，与安大通立场无任何关联",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        actions = listOf(
            TrailingAction(Icons.Outlined.Refresh, "刷新") { viewModel.refresh() }
        ),
        verticalArrangement = Arrangement.Top,
        bottomPadding = 24.dp,
        lazyContent = {
            item(key = "hot_entry") { WallHotEntry(onClick = onOpenHot) }
            if (ui.syncPaused) {
                item(key = "sync_paused") { SyncPausedBanner() }
            }
            when {
                ui.initialLoading -> item(key = "loading") {
                    AppStateCard.Loading(modifier = Modifier.fillMaxSize())
                }
                ui.failed && ui.posts.isEmpty() -> item(key = "error") {
                    AppStateCard.Error(
                        title = "校园圈暂时无法加载",
                        message = "加载失败，点击重试",
                        retryLabel = "重试",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ui.posts.isEmpty() -> item(key = "empty") {
                    AppStateCard.Empty("暂无内容", modifier = Modifier.fillMaxSize())
                }
                else -> {
                    items(ui.posts, key = { it.id }) { post ->
                        WallPostRow(
                            post = post,
                            onClick = { onOpenTopic(post.id) },
                            onOpenImage = openImage,
                        )
                    }
                    item(key = "footer") {
                        WallFeedFooter(
                            loadingMore = ui.loadingMore,
                            endReached = ui.endReached,
                            failed = ui.loadMoreFailed,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                }
            }

        }
    )
}

@Composable
internal fun rememberWallImageOpener(): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context) {
        { url: String ->
            scope.launch {
                runCatching { openWallImage(context, url) }
                    .onFailure { Toast.makeText(context, "无法打开图片", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

@Composable
private fun WallHotEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(com.ahu.ahutong.R.drawable.ic_fire),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "热榜",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun WallPostRow(
    post: WallPost,
    onClick: () -> Unit,
    onOpenImage: (String) -> Unit,
    previewComments: List<WallComment> = emptyList(),
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        WallPostBody(
            post = post,
            onClick = onClick,
            onOpenImage = onOpenImage,
            previewComments = previewComments,
            commentTotal = post.comments,
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
internal fun WallPostBody(
    post: WallPost,
    onClick: (() -> Unit)? = null,
    onOpenImage: ((String) -> Unit)? = null,
    maxLines: Int = 6,
    previewComments: List<WallComment> = emptyList(),
    commentTotal: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        WallAvatar(url = post.author?.avatar, size = 40.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.author?.name?.takeIf { it.isNotBlank() } ?: "神秘同学",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatWallTime(post.createTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    text = post.likes.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            post.node?.takeIf { it.isNotBlank() }?.let { node ->
                Text(
                    text = node,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            post.title?.takeIf { it.isNotBlank() && it != "none" }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                text = post.content.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (post.masked) 0.6f else 1f),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .alpha(if (post.masked) 0.6f else 1f),
                maxLines = maxLines,
                overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
            )
            post.images?.takeIf { it.isNotEmpty() }?.let { images ->
                Spacer(Modifier.height(8.dp))
                WallImageGrid(images = images, onOpenImage = onOpenImage)
            }
            Spacer(Modifier.height(10.dp))
            if (previewComments.isNotEmpty() && commentTotal > 0) {
                WallCommentPreviewBlock(
                    comments = previewComments,
                    total = commentTotal,
                )
            }
        }
    }
}

@Composable
private fun WallCommentPreviewBlock(comments: List<WallComment>, total: Int) {
    val shown = comments.take(PREVIEW_COMMENT_LIMIT)
    if (shown.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val byId = comments.associateBy { it.id }
        shown.forEach { comment ->
            val reply = comment.replyToCommentId
                .takeIf { it != 0L }
                ?.let { byId[it]?.user?.name?.takeIf(String::isNotBlank) }
            val prefix = if (reply != null) "回复 $reply：" else ""
            val name = comment.user?.name?.takeIf { it.isNotBlank() } ?: "未知"
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(name) }
                    append("：$prefix${comment.content.orEmpty()}")
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (total > PREVIEW_COMMENT_LIMIT) {
            Text(
                text = "剩余 ${total - PREVIEW_COMMENT_LIMIT} 条评论",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun WallAvatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(Color.Transparent),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

internal fun isRejectedWallImage(url: String): Boolean =
    url.startsWith("https://img.zxs-bbs.cn/common/") || url.startsWith("http://img.zxs-bbs.cn/common/")

@Composable
private fun WallImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "图片不可见",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun WallImageGrid(images: List<String>, onOpenImage: ((String) -> Unit)? = null) {
    if (images.size == 1) {
        if (isRejectedWallImage(images.first())) {
            WallImagePlaceholder(
                Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
            )
            return
        }
        AsyncImage(
            model = images.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = onOpenImage != null) { onOpenImage?.invoke(images.first()) }
        )
        return
    }
    val columns = if (images.size == 4) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.chunked(columns).forEach { rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowImages.forEach { url ->
                    if (isRejectedWallImage(url)) {
                        WallImagePlaceholder(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                        return@forEach
                    }
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = onOpenImage != null) { onOpenImage?.invoke(url) }
                    )
                }
                repeat(columns - rowImages.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun WallStat(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncPausedBanner() {
    Text(
        text = "数据同步暂停中",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun WallFeedFooter(
    loadingMore: Boolean,
    endReached: Boolean,
    failed: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(loadingMore, endReached, failed) {
        if (!loadingMore && !endReached && !failed) onLoadMore()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loadingMore -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                Text(
                    text = "正在加载…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            failed -> Text(
                text = "加载失败，点击重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onLoadMore)
            )
            endReached -> Text(
                text = "没有更多了",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CampusWallTopicScreen(
    onBack: (() -> Unit)? = null,
    viewModel: CampusWallTopicViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val openImage = rememberWallImageOpener()
    AppPageScaffold(
        title = "帖子",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        bottomPadding = 32.dp,
        lazyContent = {
            val topic = state.topic
            when {
                state.loading -> item(key = "loading") {
                    AppStateCard.Loading(modifier = Modifier.fillMaxSize())
                }
                state.failed || topic == null -> item(key = "error") {
                    AppStateCard.Error(
                        title = "帖子暂时无法加载",
                        message = "加载失败，点击重试",
                        retryLabel = "重试",
                        onRetry = viewModel::load,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    val comments = topic.commentList.orEmpty()
                    val byId = comments.associateBy { it.id }
                    item(key = "post") {
                        WallPostBody(
                            post = topic.toPost(),
                            onOpenImage = openImage,
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                    item(key = "comments_header") {
                        Text(
                            text = "评论 ${topic.comments}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    if (comments.isEmpty()) {
                        item(key = "comments_empty") {
                            Text(
                                text = if (topic.comments > 0) "评论同步中" else "还没有评论",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        val topLevel = comments.filter { it.parentCommentId == 0L }
                        val replies = comments
                            .filter { it.parentCommentId != 0L }
                            .groupBy { it.parentCommentId }
                        items(topLevel, key = { "top_${it.id}" }) { comment ->
                            WallCommentThread(
                                comment = comment,
                                replies = replies[comment.id].orEmpty(),
                                byId = byId,
                                onOpenImage = openImage,
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun WallCommentThread(
    comment: WallComment,
    replies: List<WallComment>,
    byId: Map<Long, WallComment>,
    onOpenImage: ((String) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WallCommentRow(comment = comment, byId = byId, onOpenImage = onOpenImage)
        replies.forEach { reply ->
            WallCommentRow(
                comment = reply,
                byId = byId,
                nested = true,
                modifier = Modifier.padding(start = 44.dp),
                onOpenImage = onOpenImage,
            )
        }
    }
}

@Composable
private fun WallCommentRow(
    comment: WallComment,
    byId: Map<Long, WallComment>,
    nested: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenImage: ((String) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (nested) 4.dp else 8.dp)
    ) {
        WallAvatar(url = comment.user?.avatar, size = if (nested) 24.dp else 32.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = comment.user?.name?.takeIf { it.isNotBlank() } ?: "未知",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            val replyName = comment.replyToCommentId
                .takeIf { it != 0L }
                ?.let { byId[it]?.user?.name?.takeIf { name -> name.isNotBlank() } }
            Text(
                text = buildString {
                    if (replyName != null) append("回复 $replyName：")
                    append(comment.content.orEmpty())
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
            comment.images?.takeIf { it.isNotEmpty() }?.let { images ->
                Spacer(Modifier.height(6.dp))
                WallImageGrid(images = images, onOpenImage = onOpenImage)
            }
            Text(
                text = formatWallTime(comment.createTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

internal suspend fun openWallImage(context: Context, url: String) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request)
    val bitmap = (result as? SuccessResult)?.drawable?.let { it as? BitmapDrawable }?.bitmap
        ?: throw IllegalStateException("image decode failed")
    val directory = File(context.cacheDir, "wall-images").apply { mkdirs() }
    val file = File(directory, "wall_${url.hashCode().toUInt()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}

private fun WallTopic.toPost() = WallPost(
    id = id,
    node = node,
    title = title,
    content = content,
    images = images,
    likes = likes,
    comments = comments,
    views = views,
    createTime = createTime,
    createdAtTs = createdAtTs,
    author = author,
    masked = masked,
)

private fun formatWallTime(raw: String?): String {
    val text = raw?.takeIf { it.length >= 16 } ?: return ""
    return text.substring(5, 16)
}
