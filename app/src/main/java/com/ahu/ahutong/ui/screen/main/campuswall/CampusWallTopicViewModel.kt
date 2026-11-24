package com.ahu.ahutong.ui.screen.main.campuswall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.wall.CampusWallRepository
import com.ahu.ahutong.data.wall.WallPost
import com.ahu.ahutong.data.wall.WallTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CampusWallTopicUiState(
    val loading: Boolean = true,
    val topic: WallTopic? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class CampusWallTopicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CampusWallRepository,
) : ViewModel() {

    private val topicId: Long = savedStateHandle.get<Long>("id") ?: 0L

    private val _ui = MutableStateFlow(CampusWallTopicUiState())
    val ui: StateFlow<CampusWallTopicUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (topicId <= 0) {
            _ui.value = CampusWallTopicUiState(loading = false, failed = true)
            return
        }
        _ui.value = CampusWallTopicUiState(loading = true)
        viewModelScope.launch {
            _ui.value = when (val result = repository.topic(topicId)) {
                is AhuResult.Success -> CampusWallTopicUiState(loading = false, topic = result.value)
                is AhuResult.Failure -> {
                    val fallback = CampusWallHotCache.posts[topicId]
                    if (fallback != null) {
                        CampusWallTopicUiState(loading = false, topic = fallback.toTopic())
                    } else {
                        CampusWallTopicUiState(loading = false, failed = true)
                    }
                }
            }
        }
    }
}

private fun WallPost.toTopic() = WallTopic(
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
    commentList = emptyList(),
)
