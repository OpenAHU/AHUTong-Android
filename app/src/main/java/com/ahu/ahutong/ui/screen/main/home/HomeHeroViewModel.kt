package com.ahu.ahutong.ui.screen.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.storage.HomeHeroSlot
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页 Hero 的作业候选：只读本地缓存，不发起同步。
 * 进入首页时顺带触发一次"最紧急一条"的静默状态校准（服务内部节流）。
 */
@HiltViewModel
internal class HomeHeroViewModel @Inject constructor(
    private val store: ChaoxingStore,
    private val settings: SettingsStore,
    private val syncService: ChaoxingSyncService,
) : ViewModel() {

    private val _assignments = MutableStateFlow<List<HomeAssignment>>(emptyList())
    val assignments: StateFlow<List<HomeAssignment>> = _assignments.asStateFlow()

    /** 用户可拖动调整的 Hero 上屏顺序。 */
    val heroPriority: StateFlow<List<HomeHeroSlot>> = settings.homeHeroPriority
        .map { HomeHeroSlot.normalize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeHeroSlot.DEFAULT_ORDER)

    init {
        viewModelScope.launch {
            syncService.state
                .map { it.worksRevision }
                .distinctUntilChanged()
                .collect { load() }
        }
    }

    fun refresh() {
        syncService.refreshMostUrgentWorkStatus()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val works = withContext(Dispatchers.IO) { store.works() }
        _assignments.value = works.mapNotNull { work ->
            val endTs = work.endTs ?: return@mapNotNull null
            if (work.isDone) return@mapNotNull null
            HomeAssignment(
                title = work.title,
                courseName = work.courseName,
                endTs = endTs
            )
        }
    }
}
