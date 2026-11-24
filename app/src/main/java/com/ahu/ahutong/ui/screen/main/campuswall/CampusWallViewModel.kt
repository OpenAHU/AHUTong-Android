package com.ahu.ahutong.ui.screen.main.campuswall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.wall.CampusWallRepository
import com.ahu.ahutong.data.wall.WallComment
import com.ahu.ahutong.data.wall.WallPost
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampusWallUiState(
    val posts: List<WallPost> = emptyList(),
    val postComments: Map<Long, List<WallComment>> = emptyMap(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val failed: Boolean = false,
    val lastSyncAt: Long? = null,
    val syncPaused: Boolean = false,
)

@HiltViewModel
class CampusWallViewModel @Inject constructor(
    private val repository: CampusWallRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CampusWallUiState())
    val ui: StateFlow<CampusWallUiState> = _ui.asStateFlow()

    private val commentsLoaded = mutableSetOf<Long>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasPosts = _ui.value.posts.isNotEmpty()
            _ui.update {
                it.copy(
                    refreshing = hasPosts,
                    initialLoading = !hasPosts,
                    failed = false,
                    loadMoreFailed = false,
                )
            }
            val status = repository.status()
            val feed = repository.feed(limit = PAGE_SIZE)
            val syncAt = (status as? AhuResult.Success)?.value?.lastSyncAt?.takeIf { it > 0 }
            val statusNow = (status as? AhuResult.Success)?.value?.now?.takeIf { it > 0 }
            val tokenExp = (status as? AhuResult.Success)?.value?.tokenExp
            val paused = if (statusNow != null && syncAt != null) {
                statusNow - syncAt > STALE_AFTER_SECONDS || tokenExp == null || tokenExp < statusNow
            } else {
                false
            }
            when (feed) {
                is AhuResult.Success -> {
                    val posts = feed.value.items.orEmpty()
                    commentsLoaded.clear()
                    loadCommentPreviews(posts)
                    _ui.update {
                        it.copy(
                            posts = posts,
                            postComments = emptyMap(),
                            initialLoading = false,
                            refreshing = false,
                            loadingMore = false,
                            endReached = posts.size < PAGE_SIZE,
                            failed = false,
                            lastSyncAt = syncAt,
                            syncPaused = paused,
                        )
                    }
                }
                is AhuResult.Failure -> {
                    _ui.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            failed = it.posts.isEmpty(),
                            lastSyncAt = syncAt ?: it.lastSyncAt,
                            syncPaused = paused,
                        )
                    }
                }
            }
        }
    }

    fun loadMore() {
        val current = _ui.value
        if (current.loadingMore || current.endReached || current.initialLoading || current.posts.isEmpty()) return
        val last = current.posts.lastOrNull() ?: return
        _ui.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            when (val result = repository.feed(limit = PAGE_SIZE, beforeTs = last.createdAtTs, beforeId = last.id)) {
                is AhuResult.Success -> {
                    val more = result.value.items.orEmpty()
                    loadCommentPreviews(more)
                    _ui.update {
                        it.copy(
                            posts = it.posts + more,
                            loadingMore = false,
                            endReached = more.size < PAGE_SIZE,
                        )
                    }
                }
                is AhuResult.Failure -> _ui.update { it.copy(loadingMore = false, loadMoreFailed = true) }
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
                    is AhuResult.Success -> _ui.update { state ->
                        state.copy(
                            postComments = state.postComments +
                                (post.id to result.value.commentList.orEmpty())
                        )
                    }
                    is AhuResult.Failure -> commentsLoaded.remove(post.id)
                }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val STALE_AFTER_SECONDS = 30 * 60L
    }
}
