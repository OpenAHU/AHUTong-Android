package com.ahu.ahutong.ui.screen.main.campuswall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.wall.CampusWallRepository
import com.ahu.ahutong.data.wall.WallComment
import com.ahu.ahutong.data.wall.WallPost
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 热榜帖可能已超出 3 天窗口，详情 404 时用这份缓存兜底展示正文。 */
internal object CampusWallHotCache {
    val posts = mutableMapOf<Long, WallPost>()
}

data class CampusHotUiState(
    val loading: Boolean = true,
    val posts: List<WallPost> = emptyList(),
    val postComments: Map<Long, List<WallComment>> = emptyMap(),
    val failed: Boolean = false,
    val stale: Boolean = false,
)

@HiltViewModel
class CampusHotViewModel @Inject constructor(
    private val repository: CampusWallRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CampusHotUiState())
    val ui: StateFlow<CampusHotUiState> = _ui.asStateFlow()

    private val commentsLoaded = mutableSetOf<Long>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, failed = false)
            when (val result = repository.hot()) {
                is AhuResult.Success -> {
                    val posts = result.value.items.orEmpty()
                    posts.forEach { CampusWallHotCache.posts[it.id] = it }
                    commentsLoaded.clear()
                    loadCommentPreviews(posts)
                    _ui.value = CampusHotUiState(
                        loading = false,
                        posts = posts,
                        postComments = emptyMap(),
                        stale = result.value.stale,
                    )
                }
                is AhuResult.Failure -> _ui.value = _ui.value.copy(loading = false, failed = true)
            }
        }
    }

    private fun loadCommentPreviews(posts: List<WallPost>) {
        val pending = posts.filter { it.comments > 0 && it.id !in commentsLoaded }
        if (pending.isEmpty()) return
        pending.forEach { commentsLoaded.add(it.id) }
        viewModelScope.launch {
            for (post in pending) {
                when (val result = repository.topic(post.id)) {
                    is AhuResult.Success -> _ui.value = _ui.value.copy(
                        postComments = _ui.value.postComments +
                            (post.id to result.value.commentList.orEmpty())
                    )
                    is AhuResult.Failure -> commentsLoaded.remove(post.id)
                }
            }
        }
    }
}

@Composable
fun CampusHotScreen(
    onBack: (() -> Unit)? = null,
    onOpenTopic: (Long) -> Unit = {},
    viewModel: CampusHotViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val openImage = rememberWallImageOpener()
    AppPageScaffold(
        title = "热榜",
        subtitle = "只读展示第三方平台内容，与安大通立场无任何关联",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        bottomPadding = 24.dp,
        lazyContent = {
            when {
                ui.loading -> item(key = "loading") {
                    AppStateCard.Loading(modifier = Modifier.fillMaxSize())
                }
                ui.failed -> item(key = "error") {
                    AppStateCard.Error(
                        title = "热榜暂时无法加载",
                        message = "加载失败，点击重试",
                        retryLabel = "重试",
                        onRetry = viewModel::load,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ui.posts.isEmpty() -> item(key = "empty") {
                    AppStateCard.Empty("暂无内容", modifier = Modifier.fillMaxSize())
                }
                else -> items(ui.posts, key = { it.id }) { post ->
                    WallPostRow(
                        post = post,
                        onClick = { onOpenTopic(post.id) },
                        onOpenImage = openImage,
                        previewComments = ui.postComments[post.id].orEmpty(),
                    )
                }
            }
        }
    )
}