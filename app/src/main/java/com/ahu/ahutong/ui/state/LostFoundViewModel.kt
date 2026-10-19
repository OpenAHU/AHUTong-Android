package com.ahu.ahutong.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ahu.ahutong.data.session.SessionStore

@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    // 校区
    var allCampus by mutableStateOf<AllCampus?>(null)
    var campusLoading by mutableStateOf(false)

    // 类型
    var allLostFoundType by mutableStateOf<AllLostFoundType?>(null)
    var typeLoading by mutableStateOf(false)

    // 当前显示的帖子列表
    var lostFoundList by mutableStateOf<List<LostFoundItem>>(emptyList())

    // 当前状态
    // 1=失物招领
    // 2=寻物启事
    var currentState by mutableStateOf(1)
        private set
    var selectedCampus by mutableStateOf<String?>(null)
        private set
    var selectedType by mutableStateOf<String?>(null)
        private set
    var presetCandidates by mutableStateOf<List<PresetCandidate>>(emptyList())
        private set
    private var filterCommitJob: Job? = null
    private var listRequestJob: Job? = null
    private var activePresetInteraction: PresetInteractionToken? = null
    private var candidatesAtOpportunity: List<PresetCandidate> = emptyList()

    // 分页信息
    private var currentPage by mutableStateOf(1)
    private val pageSize = 20
    private var totalPages by mutableStateOf(1)

    // 加载状态
    var listLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set
    val currentUserName: String
        get() = SessionStore.currentUser()?.xh ?: "null"

    var errorMessage by mutableStateOf<String?>(null)

    var myPosts by mutableStateOf<List<LostFoundItem>>(emptyList())
        private set
    var myPostsLoading by mutableStateOf(false)
        private set
    var myPostsError by mutableStateOf<String?>(null)
        private set
    var isPublishing by mutableStateOf(false)
        private set
    var deletingPostIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 是否还有更多数据
     */
    val hasMore: Boolean
        get() = currentPage < totalPages

    /**
     * 获取校区列表
     */
    fun getAllCampus(
        forceRefresh: Boolean = false
    ) = viewModelScope.launch {
        campusLoading = true

        try {
            // 非强制刷新先读缓存
            if (!forceRefresh && !AHUCache.getMockData()) {
                val cache =
                    withContext(Dispatchers.IO) { AHUCache.getLostFoundCampus() }

                if (cache.isNotEmpty()) {
                    allCampus = AllCampus(
                        code = 0,
                        msg = "cache",
                        `object` = cache
                    )
                }
            }

            // 联网更新
            val result =
                AHURepository.getAllCampus()

            val campusPayload = result.valueOrNull()
            if (campusPayload != null) {
                allCampus = campusPayload

                withContext(Dispatchers.IO) { AHUCache.saveLostFoundCampus(campusPayload.`object`) }

                errorMessage = null
            } else {
                errorMessage = result.errorOrNull()?.toUserMessage()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // 没缓存时才报错
            if (allCampus == null) {
                errorMessage =
                    t.message ?: "获取校区失败"
            }
        } finally {
            campusLoading = false
        }
    }

    /**
     * 获取分类列表
     */
    fun getAllLostFoundType(
        forceRefresh: Boolean = false
    ) = viewModelScope.launch {
        typeLoading = true

        try {
            // 非强制刷新先读缓存
            if (!forceRefresh && !AHUCache.getMockData()) {
                val cache =
                    withContext(Dispatchers.IO) { AHUCache.getLostFoundType() }

                if (cache.isNotEmpty()) {
                    allLostFoundType =
                        AllLostFoundType(
                            code = 0,
                            msg = "cache",
                            `object` = cache
                        )
                }
            }

            // 联网更新
            val result =
                AHURepository.getAllLostFoundType()

            val typePayload = result.valueOrNull()
            if (typePayload != null) {
                allLostFoundType = typePayload

                withContext(Dispatchers.IO) { AHUCache.saveLostFoundType(typePayload.`object`) }

                errorMessage = null
            } else {
                errorMessage = result.errorOrNull()?.toUserMessage()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (allLostFoundType == null) {
                errorMessage =
                    t.message ?: "获取类型失败"
            }
        } finally {
            typeLoading = false
        }
    }

    /**
     * 切换状态
     */
    fun switchState(state: Int) {
        if (currentState == state) return

        currentState = state
        currentPage = 1
        totalPages = 1

        lostFoundList = emptyList()
        fetchFirstPage(commitPresetOnDispatch = true, restoreCache = true)
    }

    fun selectCampusFilter(campusId: String?) {
        if (selectedCampus == campusId) return
        selectedCampus = campusId
        scheduleFilterCommit()
    }

    fun selectTypeFilter(typeId: String?) {
        if (selectedType == typeId) return
        selectedType = typeId
        scheduleFilterCommit()
    }

    /**
     * 获取第一页（覆盖）
     */
    fun fetchFirstPage(commitPresetOnDispatch: Boolean = false, restoreCache: Boolean = false) {
        listRequestJob?.cancel()
        val requestedState = currentState
        listRequestJob = viewModelScope.launch {
            listLoading = true
            try {
                if (restoreCache) {
                    val cached = withContext(Dispatchers.IO) {
                        if (AHUCache.getMockData()) emptyList() else AHUCache.getLostFoundList(requestedState)
                    }
                    if (currentState != requestedState) return@launch
                    lostFoundList = cached
                }
                if (commitPresetOnDispatch) recordCurrentPresetDispatch()
                val result = AHURepository.getLostFoundList(
                    pageNo = 1,
                    pageSize = pageSize,
                    state = requestedState
                )
                if (currentState != requestedState) return@launch
                val pagePayload = result.valueOrNull()
                if (pagePayload != null) {
                    val pageData = pagePayload.data
                    currentPage = pageData.pageNum
                    totalPages = pageData.pages
                    lostFoundList = pageData.list
                    withContext(Dispatchers.IO) { AHUCache.saveLostFoundList(requestedState, pageData.list) }
                    errorMessage = null
                    reportListContent(pageData.list.size, fresh = true)
                } else {
                    errorMessage = result.errorOrNull()?.toUserMessage()
                    reportListError()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (currentState == requestedState) {
                    errorMessage = t.message ?: "获取列表失败"
                    reportListError()
                }
            } finally {
                if (currentState == requestedState) listLoading = false
            }
        }
    }

    /**
     * 刷新
     */
    fun refreshList() {
        listRequestJob?.cancel()
        val requestedState = currentState
        viewModelScope.launch {
            isRefreshing = true

            try {
                getAllCampus(true)
                getAllLostFoundType(true)

                val result =
                    AHURepository.getLostFoundList(
                        pageNo = 1,
                        pageSize = pageSize,
                        state = requestedState
                    )

                if (currentState != requestedState) return@launch
                val refreshPayload = result.valueOrNull()
                if (refreshPayload != null) {
                    val pageData =
                        refreshPayload.data

                    currentPage =
                        pageData.pageNum

                    totalPages =
                        pageData.pages

                    lostFoundList =
                        pageData.list

                    withContext(Dispatchers.IO) {
                        AHUCache.clearLostFoundList(requestedState)
                        AHUCache.saveLostFoundList(requestedState, pageData.list)
                    }

                    errorMessage = null
                    reportListContent(pageData.list.size, fresh = true)
                } else {
                    errorMessage = result.errorOrNull()?.toUserMessage()
                    reportListError()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                errorMessage =
                    t.message ?: "刷新失败"
                reportListError()
            } finally {
                isRefreshing = false
            }
        }
    }

    /**
     * 加载更多
     */
    fun loadMore() {
        if (isLoadingMore || listLoading || !hasMore) return

        viewModelScope.launch {
            isLoadingMore = true
            val requestedState = currentState
            try {
                val nextPage = currentPage + 1

                val result = AHURepository.getLostFoundList(
                    pageNo = nextPage,
                    pageSize = pageSize,
                    state = requestedState
                )

                if (currentState != requestedState) return@launch
                val morePayload = result.valueOrNull()
                if (morePayload != null) {
                    val pageData = morePayload.data

                    currentPage = pageData.pageNum
                    totalPages = pageData.pages

                    val newList = pageData.list

                    lostFoundList = lostFoundList + newList

                    withContext(Dispatchers.IO) { AHUCache.appendLostFoundList(requestedState, newList) }

                    errorMessage = null
                } else {
                    errorMessage = result.errorOrNull()?.toUserMessage()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                errorMessage = t.message ?: "加载更多失败"
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * 发布帖子
     */
    fun publishLostFound(
        linkman: String,
        phone: String,
        title: String,
        num1: String,
        campusId: String,
        typeId: String,
        state: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (isPublishing) return
        viewModelScope.launch {
            isPublishing = true
            val result = runCatching {
                val response = AHURepository.publishLostFound(
                    LostFoundPublishRequest(
                        imgs = emptyList(),
                        linkman = linkman,
                        phone = phone,
                        typeid = typeId,
                        num1 = num1,
                        campusid = campusId,
                        title = title,
                        state = state,
                        auditresult = 1
                    )
                )
                check(response.isSuccess) { response.errorOrNull()?.toUserMessage() ?: "发布失败" }
            }
            if (result.isSuccess) {
                refreshList()
                loadMyPosts()
            }
            isPublishing = false
            onResult(result)
        }
    }

    fun deleteLostFound(
        id: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (id in deletingPostIds) return
        viewModelScope.launch {
            deletingPostIds = deletingPostIds + id
            val result = runCatching {
                val response = AHURepository.deleteLostFound(id)
                check(response.isSuccess) { response.errorOrNull()?.toUserMessage() ?: "删除失败" }
            }
            if (result.isSuccess) {
                lostFoundList = lostFoundList.filterNot { it.id == id }
                myPosts = myPosts.filterNot { it.id == id }
                refreshList()
            }
            deletingPostIds = deletingPostIds - id
            onResult(result)
        }
    }

    fun loadMyPosts() {
        if (myPostsLoading) return
        viewModelScope.launch {
            myPostsLoading = true
            myPostsError = null
            val result = runCatching {
                coroutineScope {
                    val found = async { loadAllPostsForState(1) }
                    val wanted = async { loadAllPostsForState(2) }
                    (found.await() + wanted.await())
                        .filter { item ->
                            item.createuser == currentUserName ||
                                item.pubuser?.idNumber == currentUserName
                        }
                        .distinctBy(LostFoundItem::id)
                        .sortedByDescending(LostFoundItem::createtime)
                }
            }
            result.onSuccess { myPosts = it }
                .onFailure { myPostsError = it.message ?: "加载我的帖子失败" }
            myPostsLoading = false
        }
    }

    private suspend fun loadAllPostsForState(state: Int): List<LostFoundItem> {
        val posts = mutableListOf<LostFoundItem>()
        var page = 1
        var pages = 1
        do {
            val response = AHURepository.getLostFoundList(
                pageNo = page,
                pageSize = MY_POST_PAGE_SIZE,
                state = state
            )
            check(response.isSuccess) { response.errorOrNull()?.toUserMessage() ?: "加载帖子失败" }
            val payload = response.valueOrNull()
                ?: error(response.errorOrNull()?.toUserMessage() ?: "加载帖子失败")
            posts += payload.data.list
            pages = payload.data.pages.coerceAtLeast(1)
            page++
        } while (page <= pages)
        return posts
    }

    fun applyPresetCandidate(candidate: PresetCandidate) = viewModelScope.launch {
        filterCommitJob?.cancel()
        val applied = behaviorRuntime.applyLocalPreset(candidate) ?: return@launch
        activePresetInteraction = applied.interactionToken
        candidatesAtOpportunity = presetCandidates
        val decoded = runCatching { Gson().fromJson(applied.localPayloadJson, LostFoundPresetPayload::class.java) }.getOrNull()
            ?: return@launch
        if (decoded.state !in 1..2) return@launch
        currentState = decoded.state
        selectedCampus = decoded.campusId?.takeIf { candidateId ->
            allCampus?.`object`.orEmpty().any { it.id == candidateId }
        }
        selectedType = decoded.typeId?.takeIf { candidateId ->
            allLostFoundType?.`object`.orEmpty().any { it.typeId == candidateId }
        }
        presetCandidates = emptyList()
        currentPage = 1
        totalPages = 1
        fetchFirstPage(commitPresetOnDispatch = true)
    }

    private fun scheduleFilterCommit() {
        filterCommitJob?.cancel()
        filterCommitJob = viewModelScope.launch {
            delay(FILTER_SETTLE_MS)
            recordCurrentPresetDispatch()
        }
    }

    private suspend fun recordCurrentPresetDispatch() {
        val payload = LostFoundPresetPayload(currentState, selectedCampus, selectedType)
        val coarse = LostFoundCoarsePreset(
            postCategory = if (currentState == 1) "LOST" else "FOUND",
            campusCategory = if (selectedCampus == null) "ALL" else "SELECTED",
            itemCategory = if (selectedType == null) "ALL" else "SELECTED"
        )
        behaviorRuntime.recordNaturalPresetSubmission(
            PresetSubmission(
                SemanticDomain.LOST_FOUND,
                Gson().toJson(payload),
                Gson().toJson(coarse),
                "$currentState|${selectedCampus.orEmpty()}|${selectedType.orEmpty()}"
            ),
            interactionToken = activePresetInteraction,
            candidatesAtOpportunity = candidatesAtOpportunity.ifEmpty { presetCandidates }
        )
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
        presetCandidates = behaviorRuntime.rankLocalPresets(SemanticDomain.LOST_FOUND)
    }

    fun onPresetCandidateVisible(candidate: PresetCandidate) = viewModelScope.launch {
        val token = behaviorRuntime.markPresetRecommendationExposed(candidate) ?: return@launch
        activePresetInteraction = token
        candidatesAtOpportunity = presetCandidates
    }

    fun onPresetSurfaceDisposed() {
        behaviorRuntime.expirePresetInteractionAsync(activePresetInteraction)
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
    }

    private fun reportListContent(count: Int, fresh: Boolean) {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.LOST_FOUND,
            if (count == 0) ContentStateBucket.EMPTY else ContentStateBucket.READY,
            freshnessBucket = if (fresh) 0 else 2,
            resultCount = resultCountBucket(count)
        )
    }

    private fun reportListError() {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.LOST_FOUND,
            ContentStateBucket.ERROR,
            freshnessBucket = 7,
            resultCount = ResultCountBucket.ZERO,
            errorType = ErrorTypeBucket.NETWORK
        )
    }

    private fun resultCountBucket(count: Int): ResultCountBucket = when (count) {
        0 -> ResultCountBucket.ZERO
        in 1..5 -> ResultCountBucket.ONE_TO_FIVE
        in 6..20 -> ResultCountBucket.SIX_TO_TWENTY
        else -> ResultCountBucket.TWENTY_ONE_PLUS
    }

    init {
        getAllCampus()
        getAllLostFoundType()

        fetchFirstPage(restoreCache = true)
        viewModelScope.launch {
            presetCandidates = behaviorRuntime.rankLocalPresets(SemanticDomain.LOST_FOUND)
        }
    }

    private companion object {
        const val FILTER_SETTLE_MS = 800L
        const val MY_POST_PAGE_SIZE = 100
    }

    override fun onCleared() {
        filterCommitJob?.cancel()
        listRequestJob?.cancel()
        onPresetSurfaceDisposed()
        super.onCleared()
    }
}

private data class LostFoundPresetPayload(val state: Int, val campusId: String?, val typeId: String?)
private data class LostFoundCoarsePreset(
    val postCategory: String,
    val campusCategory: String,
    val itemCategory: String
)
