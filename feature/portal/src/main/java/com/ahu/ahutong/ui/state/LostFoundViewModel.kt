package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.portal.LostFoundLocalStore
import com.ahu.ahutong.data.portal.LostFoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val lostFoundRepository: LostFoundRepository,
    private val lostFoundLocalStore: LostFoundLocalStore,
) : ViewModel() {

    var allCampus by mutableStateOf<AllCampus?>(null)
    var campusLoading by mutableStateOf(false)

    var allLostFoundType by mutableStateOf<AllLostFoundType?>(null)
    var typeLoading by mutableStateOf(false)

    var lostFoundList by mutableStateOf<List<LostFoundItem>>(emptyList())

    // 1=失物招领 2=寻物启事
    var currentState by mutableStateOf(1)
        private set

    private var currentPage by mutableStateOf(1)
    private val pageSize = 20
    private var totalPages by mutableStateOf(1)

    var listLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    val currentUserName: String
        get() = lostFoundLocalStore.getCurrentUserId() ?: "null"

    var errorMessage by mutableStateOf<String?>(null)

    val hasMore: Boolean
        get() = currentPage < totalPages

    fun isMockMode(): Boolean = lostFoundLocalStore.isMockMode()

    fun getAllCampus(forceRefresh: Boolean = false) = viewModelScope.launch {
        campusLoading = true
        try {
            if (!forceRefresh && !lostFoundLocalStore.isMockMode()) {
                val cache = lostFoundLocalStore.getCachedCampus()
                if (cache.isNotEmpty()) {
                    allCampus = AllCampus(code = 0, msg = "cache", `object` = cache)
                }
            }

            when (val result = lostFoundRepository.getAllCampus(forceRefresh)) {
                is AppResult.Success -> {
                    allCampus = result.data
                    errorMessage = null
                    Log.d("lostfound", "allcampus = ${result.data}")
                }
                is AppResult.Error -> {
                    if (allCampus == null) {
                        errorMessage = result.message
                    }
                }
            }
        } catch (t: Throwable) {
            if (allCampus == null) {
                errorMessage = t.message ?: "获取校区失败"
            }
        } finally {
            campusLoading = false
        }
    }

    fun getAllLostFoundType(forceRefresh: Boolean = false) = viewModelScope.launch {
        typeLoading = true
        try {
            if (!forceRefresh && !lostFoundLocalStore.isMockMode()) {
                val cache = lostFoundLocalStore.getCachedTypes()
                if (cache.isNotEmpty()) {
                    allLostFoundType = AllLostFoundType(code = 0, msg = "cache", `object` = cache)
                }
            }

            when (val result = lostFoundRepository.getAllTypes(forceRefresh)) {
                is AppResult.Success -> {
                    allLostFoundType = result.data
                    errorMessage = null
                    Log.d("lostfound", "alltype = ${result.data}")
                }
                is AppResult.Error -> {
                    if (allLostFoundType == null) {
                        errorMessage = result.message
                    }
                }
            }
        } catch (t: Throwable) {
            if (allLostFoundType == null) {
                errorMessage = t.message ?: "获取类型失败"
            }
        } finally {
            typeLoading = false
        }
    }

    fun switchState(state: Int) {
        if (currentState == state) return
        currentState = state
        currentPage = 1
        totalPages = 1
        lostFoundList = if (lostFoundLocalStore.isMockMode()) {
            emptyList()
        } else {
            lostFoundLocalStore.getCachedList(state)
        }
        fetchFirstPage()
    }

    fun fetchFirstPage() = viewModelScope.launch {
        listLoading = true
        try {
            when (
                val result = lostFoundRepository.getList(
                    pageNo = 1,
                    pageSize = pageSize,
                    state = currentState,
                )
            ) {
                is AppResult.Success -> {
                    val pageData = result.data.data
                    currentPage = pageData.pageNum
                    totalPages = pageData.pages
                    lostFoundList = pageData.list
                    errorMessage = null
                    Log.d("lostfound", "alllist = ${result.data}")
                }
                is AppResult.Error -> {
                    errorMessage = result.message
                }
            }
        } catch (t: Throwable) {
            errorMessage = t.message ?: "获取列表失败"
        } finally {
            listLoading = false
        }
    }

    fun refreshList() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                getAllCampus(true)
                getAllLostFoundType(true)

                when (
                    val result = lostFoundRepository.getList(
                        pageNo = 1,
                        pageSize = pageSize,
                        state = currentState,
                    )
                ) {
                    is AppResult.Success -> {
                        val pageData = result.data.data
                        currentPage = pageData.pageNum
                        totalPages = pageData.pages
                        lostFoundList = pageData.list
                        errorMessage = null
                    }
                    is AppResult.Error -> {
                        errorMessage = result.message
                    }
                }
            } catch (t: Throwable) {
                errorMessage = t.message ?: "刷新失败"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || listLoading || !hasMore) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val nextPage = currentPage + 1
                when (
                    val result = lostFoundRepository.getList(
                        pageNo = nextPage,
                        pageSize = pageSize,
                        state = currentState,
                    )
                ) {
                    is AppResult.Success -> {
                        val pageData = result.data.data
                        currentPage = pageData.pageNum
                        totalPages = pageData.pages
                        lostFoundList = lostFoundList + pageData.list
                        errorMessage = null
                    }
                    is AppResult.Error -> {
                        errorMessage = result.message
                    }
                }
            } catch (t: Throwable) {
                errorMessage = t.message ?: "加载更多失败"
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun publishLostFound(
        linkman: String,
        phone: String,
        title: String,
        num1: String,
        campusId: String,
        typeId: String,
        state: String,
    ) {
        viewModelScope.launch {
            lostFoundRepository.publish(
                LostFoundPublishRequest(
                    imgs = emptyList(),
                    linkman = linkman,
                    phone = phone,
                    typeid = typeId,
                    num1 = num1,
                    campusid = campusId,
                    title = title,
                    state = state,
                    auditresult = 1,
                ),
            )
            refreshList()
        }
    }

    fun deleteLostFound(id: String) {
        viewModelScope.launch {
            try {
                when (lostFoundRepository.delete(id)) {
                    is AppResult.Success -> {
                        lostFoundList = lostFoundList.filterNot { it.id == id }
                        refreshList()
                    }
                    is AppResult.Error -> Unit
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        getAllCampus()
        getAllLostFoundType()
        lostFoundList = if (lostFoundLocalStore.isMockMode()) {
            emptyList()
        } else {
            lostFoundLocalStore.getCachedList(currentState)
        }
        fetchFirstPage()
    }
}
