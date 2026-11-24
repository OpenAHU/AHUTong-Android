package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.laundry.LaundryAuthException
import com.ahu.ahutong.data.laundry.LaundryDevice
import com.ahu.ahutong.data.laundry.LaundryDeviceTable
import com.ahu.ahutong.data.laundry.LaundryKind
import com.ahu.ahutong.data.laundry.LaundryOccupancy
import com.ahu.ahutong.data.laundry.LaundryRepository
import com.ahu.ahutong.data.laundry.LaundryTable
import com.ahu.ahutong.data.laundry.LaundryToken
import com.ahu.ahutong.data.laundry.LaundryTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 洗衣房页状态源。
 *
 * 数据三层：assets 基线（离线兜底）→ 本地缓存 → 线上台账（ETag/304，后台增量）。
 * 占用状态按**可见窗口**查询：屏幕内的机器才发请求，滚出即取消，
 * 所以蕙园 121 台也不怕——翻到哪查到哪。
 */
@HiltViewModel
class LaundryViewModel @Inject constructor(
    private val table: LaundryDeviceTable,
    private val repository: LaundryRepository,
    private val tokenStore: LaundryTokenStore
) : ViewModel() {

    data class DeviceRow(val device: LaundryDevice, val occupancy: LaundryOccupancy?)

    data class UiState(
        val loaded: Boolean = false,
        val parks: List<String> = emptyList(),
        val parkIndex: Int = 0,
        val kind: LaundryKind = LaundryKind.WASHER,
        val rows: List<DeviceRow> = emptyList(),
        val tableUpdatedAt: String? = null,
        val token: String? = null,
        val tokenExpiresAt: Long? = null,
        val refreshing: Boolean = false,
        val updatedAt: Long? = null,
        val hint: String? = null,
        val tokenError: String? = null
    ) {
        val freeCount: Int get() = rows.count { it.occupancy is LaundryOccupancy.Idle }
        val busyCount: Int get() = rows.count { it.occupancy is LaundryOccupancy.InUse }
        val hasToken: Boolean get() = !token.isNullOrBlank()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var devices: List<LaundryDevice> = emptyList()
    private var occupancies: Map<String, LaundryOccupancy> = emptyMap()
    private var refreshJob: Job? = null
    /** 台账线上检查节流时间戳（毫秒）。 */
    private var lastTableCheck = System.currentTimeMillis()
    /** 可见窗口（rows 下标区间），null = 未上报（首次进入按整表查）。 */
    private var lastRange: IntRange? = null

    init {
        viewModelScope.launch {
            val loaded = table.load()
            devices = loaded.devices
            val token = tokenStore.token()
            _state.value = _state.value.copy(
                loaded = true,
                parks = loaded.parks,
                parkIndex = defaultPark(loaded.parks),
                tableUpdatedAt = loaded.updatedAt.takeIf { it.isNotBlank() },
                token = token,
                tokenExpiresAt = token?.let(LaundryToken::expiresAtMillis)
            )
            rebuild()
            // 线上台账增量升级：304/失败都静默，基线已兜底；成功后重建列表
            upgradeTable()
        }
    }

    /** 上报当前可见窗口（rows 下标区间，含 ±1 屏冗余）；变化即触发窗口查询。 */
    fun onVisibleRangeChanged(range: IntRange?) {
        if (range == lastRange) return
        lastRange = range
        refresh()
    }

    fun selectPark(index: Int) {
        if (index == _state.value.parkIndex) return
        occupancies = emptyMap() // 换园区重新查，别拿旧状态糊弄
        _state.value = _state.value.copy(parkIndex = index, updatedAt = null)
        rebuild()
        refresh()
    }

    fun selectKind(kind: LaundryKind) {
        if (kind == _state.value.kind) return
        _state.value = _state.value.copy(kind = kind)
        rebuild()
        refresh()
    }

    /**
     * 用户粘贴 token（整条链接或裸 JWT；也承接 ON_RESUME 剪贴板接力）。
     * 先本地验有效期，再拿一台真机探一枪，避免存进去一个已经作废的 token。
     */
    fun applyToken(raw: String) {
        val token = LaundryToken.extract(raw)
        if (token == null) {
            _state.value = _state.value.copy(tokenError = "没认出 token，请确认复制的是洗衣机页面的整条链接")
            return
        }
        if (LaundryToken.isExpired(token)) {
            _state.value = _state.value.copy(tokenError = "这个 token 已经过期了（有效期 2 小时），请重新复制一条")
            return
        }
        val probeDevice = devices.firstOrNull() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, tokenError = null)
            try {
                repository.probe(token, probeDevice.id)
                tokenStore.save(token)
                occupancies = emptyMap()
                _state.value = _state.value.copy(
                    token = token,
                    tokenExpiresAt = LaundryToken.expiresAtMillis(token),
                    refreshing = false,
                    hint = "已连上，正在刷新状态"
                )
                refresh()
            } catch (auth: LaundryAuthException) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    tokenError = "平台不认这个 token（可能刚过期），回到微信重新复制一次"
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    tokenError = "校验失败：${error.javaClass.simpleName}，请检查网络后重试"
                )
            }
        }
    }

    fun clearToken() {
        tokenStore.clear()
        occupancies = emptyMap()
        _state.value = _state.value.copy(
            token = null,
            tokenExpiresAt = null,
            updatedAt = null,
            tokenError = null,
            hint = "已断开连接"
        )
        rebuild()
    }

    fun consumeHint() {
        if (_state.value.hint != null) _state.value = _state.value.copy(hint = null)
    }

    fun clearTokenError() {
        if (_state.value.tokenError != null) _state.value = _state.value.copy(tokenError = null)
    }

    /**
     * 占用查询：只查 [lastRange] 窗口内的机器（null = 整表，仅首帧兜底）。
     * 没有 token 时清空状态直接标灰；token 过期提示续期；网络失败窗口内标未知。
     * 窗口外保留上一轮结果（滚回去先见旧值再自动翻新）。
     */
    fun refresh() {
        val current = _state.value
        if (!current.loaded) return
        maybeUpgradeTable()
        val all = visibleDevices(current)
        if (all.isEmpty()) return
        val token = current.token
        if (token.isNullOrBlank() || LaundryToken.isExpired(token)) {
            occupancies = emptyMap()
            _state.value = current.copy(refreshing = false, updatedAt = null)
            rebuild()
            return
        }
        val targets = window(all)
        if (targets.isEmpty()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            try {
                val fresh = repository.occupancy(token, targets)
                occupancies = occupancies.filterKeys { id -> all.any { it.id == id } } + fresh
                _state.value = _state.value.copy(refreshing = false, updatedAt = System.currentTimeMillis())
            } catch (auth: LaundryAuthException) {
                occupancies = emptyMap()
                _state.value = _state.value.copy(
                    refreshing = false,
                    token = null,
                    hint = "token 已过期，去微信重新复制一条链接"
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    hint = "刷新失败：${error.javaClass.simpleName}"
                )
            }
            rebuild()
        }
    }

    private suspend fun upgradeTable() {
        val upgraded = runCatching { table.refreshFromNetwork() }.getOrDefault(false)
        if (!upgraded) return
        val fresh: LaundryTable = table.load()
        devices = fresh.devices
        val current = _state.value
        // 园区列表可能变化：同名园区保留下标，否则回落默认园区
        val samePark = fresh.parks.indexOfFirst { it == current.parks.getOrNull(current.parkIndex) }
        val parkIndex = if (samePark >= 0) samePark else defaultPark(fresh.parks)
        occupancies = emptyMap()
        // 园区设备数可能变，可见窗口下标失效，等屏幕重新上报
        lastRange = null
        _state.value = current.copy(
            parks = fresh.parks,
            parkIndex = parkIndex,
            tableUpdatedAt = fresh.updatedAt.takeIf { it.isNotBlank() },
            updatedAt = null
        )
        rebuild()
        refresh()
    }

    private fun window(all: List<LaundryDevice>): List<LaundryDevice> {
        val range = lastRange ?: return all
        val from = range.first.coerceAtLeast(0)
        val to = range.last.coerceAtMost(all.lastIndex)
        return if (from > to) emptyList() else all.subList(from, to + 1)
    }

    private fun visibleDevices(state: UiState): List<LaundryDevice> =
        devices.filter {
            it.parkIndex == state.parkIndex && it.kind == state.kind
        }

    private fun rebuild() {
        val current = _state.value
        val rows = visibleDevices(current).map { device ->
            DeviceRow(device, occupancies[device.id])
        }
        _state.value = current.copy(rows = rows)
    }

    private fun defaultPark(parks: List<String>): Int {
        // 打开就落在榴园（磬苑中心区，设备最全的样板园区），没有再退回第一个
        val preferred = parks.indexOfFirst { it == "榴园" }
        return if (preferred >= 0) preferred else 0
    }

    /**
     * 台账检查节流：手动刷新/下拉刷新顺带看一眼线上有没有新版本。
     * 之前只在 ViewModel 创建时检查一次，页面常驻就永远拉不到新表。
     */
    private fun maybeUpgradeTable() {
        val now = System.currentTimeMillis()
        if (now - lastTableCheck < TABLE_CHECK_INTERVAL_MS) return
        lastTableCheck = now
        viewModelScope.launch { upgradeTable() }
    }

    private companion object {
        const val TABLE_CHECK_INTERVAL_MS = 60_000L
    }
}
