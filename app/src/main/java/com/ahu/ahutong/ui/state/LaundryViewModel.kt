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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 洗衣房页状态源。
 *
 * 数据三层：assets 基线（离线兜底）→ 本地缓存 → 线上台账（ETag/304，后台增量）。
 * 占用状态按**当前园区整查**：平台没有批量接口，就分批（[LaundryViewModel.BATCH_SIZE]）回填，
 * 边查边显示；不再按可见窗口裁剪，避免切换时取消请求把设备永久留在「—」。
 */
@HiltViewModel
class LaundryViewModel @Inject constructor(
    private val table: LaundryDeviceTable,
    private val repository: LaundryRepository,
    private val tokenStore: LaundryTokenStore
) : ViewModel() {

    data class DeviceRow(val device: LaundryDevice, val occupancy: LaundryOccupancy?)

    /** 一层楼：楼层内横向滑动的机器（方位只作为单机标签，不再分组）。 */
    data class FloorRow(val floor: Int, val rows: List<DeviceRow>)

    data class UiState(
        val loaded: Boolean = false,
        val parks: List<String> = emptyList(),
        val parkIndex: Int = 0,
        val kind: LaundryKind = LaundryKind.WASHER,
        val rows: List<DeviceRow> = emptyList(),
        /** 按楼层分组；空表示当前视图没有楼层信息，走平铺三列。 */
        val floors: List<FloorRow> = emptyList(),
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
     * 占用查询：整个园区当前机型一次查完，再按 [BATCH_SIZE] 分批回填。
     * 平台没有批量接口只能逐台打，分批是为了 100+ 台的园区边查边出、不用等整轮结束。
     * （曾经的可见窗口查询会在切换时取消上一轮，被取消那批设备永远查不到、一直显示「—」。）
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
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            try {
                for (batch in all.chunked(BATCH_SIZE)) {
                    val fresh = repository.occupancy(token, batch)
                    // 换园区/机型后旧结果不能混进来
                    occupancies = occupancies.filterKeys { id -> all.any { it.id == id } } + fresh
                    rebuild()
                }
                _state.value = _state.value.copy(refreshing = false, updatedAt = System.currentTimeMillis())
            } catch (cancel: CancellationException) {
                throw cancel
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
        _state.value = current.copy(
            parks = fresh.parks,
            parkIndex = parkIndex,
            tableUpdatedAt = fresh.updatedAt.takeIf { it.isNotBlank() },
            updatedAt = null
        )
        rebuild()
        refresh()
    }

    private fun visibleDevices(state: UiState): List<LaundryDevice> =
        devices.filter {
            it.parkIndex == state.parkIndex && it.kind == state.kind
        }

    private fun rebuild() {
        val current = _state.value
        val ordered = orderedDevices(visibleDevices(current))
        val rows = ordered.map { device -> DeviceRow(device, occupancies[device.id]) }
        _state.value = current.copy(rows = rows, floors = floorsOf(ordered, rows))
    }

    /**
     * 显示顺序：楼层信息齐全的视图按「楼层 → 方位 → 机位」重排，
     * 没有楼层的园区保持台账原顺序。
     */
    private fun orderedDevices(devices: List<LaundryDevice>): List<LaundryDevice> {
        if (devices.isEmpty() || devices.any { it.floor == null }) return devices
        return devices.sortedWith(
            compareBy(
                { it.floor },
                { it.location.orEmpty() },
                { it.slot?.toIntOrNull() ?: Int.MAX_VALUE },
                { it.id }
            )
        )
    }

    /** 按楼层分组；视图里只要有一台没有楼层就返回空（避免半分组半平铺）。 */
    private fun floorsOf(devices: List<LaundryDevice>, rows: List<DeviceRow>): List<FloorRow> {
        if (devices.isEmpty() || devices.any { it.floor == null }) return emptyList()
        return devices.indices
            .groupBy { devices[it].floor ?: 0 }
            .toSortedMap()
            .map { (floor, indices) -> FloorRow(floor, indices.map { rows[it] }) }
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

        /** 单批回填台数：一批约 1 秒，园区再大也能边查边出。 */
        const val BATCH_SIZE = 16
    }
}
