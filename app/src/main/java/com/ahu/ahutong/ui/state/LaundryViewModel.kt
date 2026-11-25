package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.laundry.LaundryAuthException
import com.ahu.ahutong.data.laundry.LaundryCampus
import com.ahu.ahutong.data.laundry.LaundryDevice
import com.ahu.ahutong.data.laundry.LaundryDeviceTable
import com.ahu.ahutong.data.laundry.LaundryKind
import com.ahu.ahutong.data.laundry.LaundryOccupancy
import com.ahu.ahutong.data.laundry.LaundryParkMatch
import com.ahu.ahutong.data.laundry.LaundryParkMatcher
import com.ahu.ahutong.data.laundry.LaundryParkStore
import com.ahu.ahutong.data.laundry.LaundryRepository
import com.ahu.ahutong.data.laundry.LaundryTable
import com.ahu.ahutong.data.laundry.LaundryToken
import com.ahu.ahutong.data.laundry.LaundryTokenStore
import com.ahu.ahutong.data.laundry.QiekjLaundryMatcher
import com.ahu.ahutong.data.laundry.QiekjLaundrySource
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
    private val tokenStore: LaundryTokenStore,
    private val parkStore: LaundryParkStore,
    private val qiekj: QiekjLaundrySource
) : ViewModel() {

    data class DeviceRow(val device: LaundryDevice, val occupancy: LaundryOccupancy?)

    /** 一层楼：楼层内横向滑动的机器（方位只作为单机标签，不再分组）。 */
    data class FloorRow(val floor: Int, val rows: List<DeviceRow>)

    data class UiState(
        val loaded: Boolean = false,
        /** 校区：磬苑（乐摇摇台账）或龙河（趣智校园实时）。 */
        val campus: LaundryCampus = LaundryCampus.QINGYUAN,
        val parks: List<String> = emptyList(),
        val parkIndex: Int = 0,
        /** 非空表示宿舍楼栋对应多个洗衣房，UI 让用户选一次。 */
        val parkPrompt: List<Int> = emptyList(),
        /** 定位说明（如「已按宿舍 榴园2号楼 定位」），只用于展示。 */
        val matchNote: String? = null,
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
    /** 台账升级任务：同时只跑一个，避免重复下载。 */
    private var tableJob: Job? = null
    /** 台账线上检查节流时间戳（毫秒）。 */
    private var lastTableCheck = System.currentTimeMillis()
    /** 龙河的机房列表（门店 → 机型 → 机房），换校区/机型才重取。 */
    private var longhePoints: List<QiekjLaundrySource.Point> = emptyList()
    /** 整店下发机型（洗鞋机）的楼栋分组，与 [longhePoints] 下标一一对应。 */
    private var shoeGroups: List<QiekjLaundryMatcher.BuildingGroup> = emptyList()
    /** 洗鞋机整店快照：换楼栋只按楼栋本地筛，不再打接口。 */
    private var shoeStore: QiekjLaundrySource.Snapshot? = null
    private var lastLongheAt = 0L

    init {
        viewModelScope.launch {
            val token = tokenStore.token()
            val campus = parkStore.campus() ?: LaundryCampus.of(dormCampus())
            _state.value = _state.value.copy(
                campus = campus,
                token = token,
                tokenExpiresAt = token?.let(LaundryToken::expiresAtMillis)
            )
            if (campus == LaundryCampus.LONGHE) {
                _state.value = _state.value.copy(loaded = true)
                loadLonghe()
            } else {
                loadQingyuan()
            }
        }
    }

    private suspend fun loadQingyuan() {
        val loaded = table.load()
        devices = loaded.devices
        val parked = resolvePark(loaded.parks)
        _state.value = _state.value.copy(
            loaded = true,
            parks = loaded.parks,
            parkIndex = parked.index,
            parkPrompt = parked.prompt,
            matchNote = parked.note,
            tableUpdatedAt = loaded.updatedAt.takeIf { it.isNotBlank() }
        )
        rebuild()
        // 占用查询必须立刻发起：台账域名在境外，慢的时候能卡几十秒，
        // 排在下载后面会让首屏长时间全是「—」，用户只能手动再刷一次。
        refresh()
        // 台账增量升级并行跑；升级成功时 upgradeTable 内部会再查一次占用。
        startTableUpgrade()
    }

    fun selectPark(index: Int) {
        val current = _state.value
        val parkName = current.parks.getOrNull(index) ?: return
        // 手选一次就记住：下次进来（含多栋楼候选）直接落这里，不被自动匹配覆盖
        parkStore.save(current.campus, current.kind, parkName)
        if (index == current.parkIndex) {
            _state.value = current.copy(parkPrompt = emptyList())
            return
        }
        occupancies = emptyMap() // 换园区重新查，别拿旧状态糊弄
        _state.value = current.copy(
            parkIndex = index,
            parkPrompt = emptyList(),
            matchNote = null,
            updatedAt = null
        )
        rebuild()
        if (current.campus == LaundryCampus.LONGHE) {
            // 洗鞋机整店快照在手：换楼栋只在本地筛，不再多打接口
            val store = shoeStore
            if (store != null && shoeGroups.isNotEmpty()) {
                applyLonghe(store, index, current.kind, shoeGroups.getOrNull(index)?.key)
                _state.value = _state.value.copy(updatedAt = lastLongheAt)
                rebuild()
            } else {
                fetchLonghe(force = true)
            }
        } else refresh()
    }

    /** 手动切校区：持久化后压过宿舍信息，直到用户再切回来。 */
    fun selectCampus(campus: LaundryCampus) {
        val current = _state.value
        if (campus == current.campus) return
        parkStore.saveCampus(campus)
        refreshJob?.cancel()
        devices = emptyList()
        longhePoints = emptyList()
        shoeGroups = emptyList()
        shoeStore = null
        occupancies = emptyMap()
        lastLongheAt = 0L
        _state.value = current.copy(
            campus = campus,
            parks = emptyList(),
            parkIndex = 0,
            parkPrompt = emptyList(),
            rows = emptyList(),
            floors = emptyList(),
            matchNote = null,
            tableUpdatedAt = null,
            updatedAt = null,
            hint = null,
            tokenError = null,
            refreshing = false
        )
        viewModelScope.launch {
            if (campus == LaundryCampus.LONGHE) loadLonghe() else loadQingyuan()
        }
    }

    /** 关掉「这栋楼有多个洗衣房」弹窗，保留当前临时园区。 */
    fun dismissParkPrompt() {
        if (_state.value.parkPrompt.isEmpty()) return
        _state.value = _state.value.copy(parkPrompt = emptyList())
    }

    fun selectKind(kind: LaundryKind) {
        val current = _state.value
        if (kind == current.kind) return
        refreshJob?.cancel()
        occupancies = emptyMap()
        _state.value = current.copy(kind = kind, updatedAt = null, matchNote = null)
        if (current.campus == LaundryCampus.LONGHE) {
            // 龙河三种机型是三个门店，机房列表要跟着换
            longhePoints = emptyList()
            shoeGroups = emptyList()
            shoeStore = null
            _state.value = _state.value.copy(
                parks = emptyList(),
                parkIndex = 0,
                rows = emptyList(),
                floors = emptyList()
            )
            viewModelScope.launch { loadLonghe() }
        } else {
            rebuild()
            refresh()
        }
    }

    /**
     * 用户粘贴 token（整条链接或裸 JWT；也承接 ON_RESUME 剪贴板接力）。
     * 先本地验有效期，再拿一台真机探一枪，避免存进去一个已经作废的 token。
     */
    fun applyToken(raw: String) {
        val token = LaundryToken.extract(raw)
        if (token == null) {
            _state.value = _state.value.copy(tokenError = "未识别到有效凭据")
            return
        }
        if (LaundryToken.isExpired(token)) {
            _state.value = _state.value.copy(tokenError = "凭据已过期，请重新获取")
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
                    hint = "凭据已导入"
                )
                refresh()
            } catch (auth: LaundryAuthException) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    tokenError = "凭据已失效，请重新获取"
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    tokenError = "校验失败，请检查网络"
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
            hint = "凭据已清除"
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
        if (current.campus == LaundryCampus.LONGHE) {
            if (longhePoints.isEmpty()) viewModelScope.launch { loadLonghe() } else fetchLonghe()
            return
        }
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
                    hint = "凭据已过期，请重新导入"
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    hint = "刷新失败，请稍后重试"
                )
            }
            rebuild()
        }
    }

    /** @return true 表示台账已升级，且占用查询已由本方法拉起。 */
    private suspend fun upgradeTable(): Boolean {
        val upgraded = runCatching { table.refreshFromNetwork() }.getOrDefault(false)
        if (!upgraded) return false
        val fresh: LaundryTable = table.load()
        devices = fresh.devices
        val current = _state.value
        // 园区列表可能变化：同名园区保留下标（手选结果不被自动匹配覆盖），否则重新定位
        val samePark = fresh.parks.indexOfFirst { it == current.parks.getOrNull(current.parkIndex) }
        val parked = if (samePark >= 0) {
            ParkResolution(samePark, emptyList(), current.matchNote)
        } else {
            resolvePark(fresh.parks)
        }
        occupancies = emptyMap()
        _state.value = current.copy(
            parks = fresh.parks,
            parkIndex = parked.index,
            parkPrompt = parked.prompt,
            matchNote = parked.note,
            tableUpdatedAt = fresh.updatedAt.takeIf { it.isNotBlank() },
            updatedAt = null
        )
        rebuild()
        refresh()
        return true
    }

    /** 校区：缓存读不到时同 [dormBuilding] 一样补查一次，避免冷启动把龙河学生落到磬苑。 */
    private suspend fun dormCampus(): String? {
        AHUCache.getDormitoryInfo()?.campus?.takeIf { it.isNotBlank() }?.let { return it }
        val fetched = runCatching { AHURepository.getDormitoryInfo() }.getOrNull()
        return (fetched as? AhuResult.Success)?.value?.campus?.takeIf { it.isNotBlank() }
    }

    /**
     * 龙河进页面/换机型：门店 → 机型 → 机房列表（缓存 5 分钟）→ 按楼栋落机房 → 查一次状态。
     * 首次最多 4 个请求（相邻至少隔 1.2 秒），之后每次刷新只打 1 个。
     */
    private suspend fun loadLonghe() {
        val current = _state.value
        _state.value = current.copy(refreshing = true, hint = null)
        val points = try {
            qiekj.points(current.kind)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            failLonghe(error)
            return
        }
        if (points.isEmpty()) {
            // 洗鞋机没有机房分组：整店拉一次，用机器名里的楼栋拆成可选机房
            val store = try {
                qiekj.machines(current.kind, null)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                failLonghe(error)
                return
            }
            shoeGroups = QiekjLaundryMatcher.buildingGroupsOf(store.items.map { it.goodsName })
            shoeStore = store
            longhePoints = shoeGroups.map {
                QiekjLaundrySource.Point(QiekjLaundrySource.ALL_POINTS, it.label)
            }
            val parks = longhePoints.map { it.name }
            if (parks.isEmpty()) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    parks = emptyList(),
                    rows = emptyList(),
                    floors = emptyList(),
                    hint = "暂无可用楼栋"
                )
                return
            }
            val parked = resolveLonghePark(parks)
            _state.value = _state.value.copy(
                parks = parks,
                parkIndex = parked.index,
                parkPrompt = parked.prompt,
                matchNote = parked.note,
                tableUpdatedAt = null
            )
            fetchLonghe(force = true, preloaded = store)
            return
        }
        shoeGroups = emptyList()
        shoeStore = null
        longhePoints = points
        val parks = longhePoints.map { it.name }
        val parked = resolveLonghePark(parks)
        _state.value = _state.value.copy(
            parks = parks,
            parkIndex = parked.index,
            parkPrompt = parked.prompt,
            matchNote = parked.note,
            tableUpdatedAt = null
        )
        fetchLonghe(force = true)
    }

    private fun failLonghe(error: Exception) {
        // 机房列表也一起清掉，下次刷新才会重走"取机房 → 查机器"，而不是拿旧点位硬查
        longhePoints = emptyList()
        shoeGroups = emptyList()
        shoeStore = null
        _state.value = _state.value.copy(
            refreshing = false,
            parks = emptyList(),
            rows = emptyList(),
            floors = emptyList(),
            hint = "加载失败，请稍后重试"
        )
    }

    private fun fetchLonghe(force: Boolean = false, preloaded: QiekjLaundrySource.Snapshot? = null) {
        val current = _state.value
        if (longhePoints.getOrNull(current.parkIndex) == null) {
            // 没有可查机房时也要收掉刷新态，否则页面一直转圈
            _state.value = current.copy(refreshing = false)
            return
        }
        val now = System.currentTimeMillis()
        if (!force && current.updatedAt != null && now - lastLongheAt < LONGHE_MIN_INTERVAL_MS) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val state = _state.value
            val point = longhePoints.getOrNull(state.parkIndex) ?: return@launch
            _state.value = state.copy(refreshing = true)
            try {
                // 整店下发机型刚拉过全店，直接复用，不再多打一次
                val snapshot = preloaded ?: qiekj.machines(state.kind, point)
                lastLongheAt = System.currentTimeMillis()
                applyLonghe(
                    snapshot = snapshot,
                    parkIndex = state.parkIndex,
                    kind = state.kind,
                    groupKey = shoeGroups.getOrNull(state.parkIndex)?.key
                )
                _state.value = _state.value.copy(
                    refreshing = false,
                    updatedAt = lastLongheAt,
                    hint = null
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // 限速冷却、网络错都走这里：保留上一次读数，只提示这次没刷上
                _state.value = _state.value.copy(
                    refreshing = false,
                    hint = "刷新失败，请稍后重试"
                )
            }
            rebuild()
        }
    }

    private fun applyLonghe(
        snapshot: QiekjLaundrySource.Snapshot,
        parkIndex: Int,
        kind: LaundryKind,
        groupKey: String? = null
    ) {
        // 整店下发机型（洗鞋机）按所选楼栋收窄；楼栋选完仍查不到就退回整店，不留空白
        val grouped = groupKey != null &&
            snapshot.items.any { QiekjLaundryMatcher.buildingGroupOf(it.goodsName)?.key == groupKey }
        val scoped = if (grouped) {
            snapshot.items.filter { QiekjLaundryMatcher.buildingGroupOf(it.goodsName)?.key == groupKey }
        } else {
            snapshot.items
        }
        // 跨楼同机位号会重名，按楼栋/楼层/机位排序，同楼机器才挨在一起
        val ordered = scoped.sortedWith(
            compareBy<com.ahu.ahutong.data.laundry.QiekjMachine>(
                { QiekjLaundryMatcher.buildingGroupOf(it.goodsName)?.key ?: "\uFFFF" },
                { QiekjLaundryMatcher.floorOf(it.goodsName) ?: Int.MAX_VALUE },
                { QiekjLaundryMatcher.slotOf(it.goodsName)?.toIntOrNull() ?: Int.MAX_VALUE }
            )
        )
        val list = ArrayList<LaundryDevice>(scoped.size)
        val fresh = HashMap<String, LaundryOccupancy>(scoped.size)
        // 整店展示时机位号跨楼重名，才需要用楼栋兜底做位置标签；同楼（含单机房）不重复写楼栋
        val showBuilding = !grouped &&
            scoped.mapNotNull { QiekjLaundryMatcher.buildingLabel(it.goodsName) }.distinct().size > 1
        ordered.forEach { machine ->
            val id = machine.goodsId?.toString() ?: return@forEach
            val floor = QiekjLaundryMatcher.floorOf(machine.goodsName)
            val slot = QiekjLaundryMatcher.slotOf(machine.goodsName)
            list += LaundryDevice(
                id = id,
                parkIndex = parkIndex,
                kind = kind,
                location = if (showBuilding) QiekjLaundryMatcher.buildingLabel(machine.goodsName) else null,
                floor = floor,
                slot = slot,
                remark = machine.goodsName?.takeIf { slot == null && !grouped },
                online = true
            )
            fresh[id] = QiekjLaundryMatcher.occupancyOf(
                status = machine.deviceWorkStatus,
                endUseTimeValue = machine.endUseTimeValue,
                clockOffsetMillis = snapshot.clockOffsetMillis
            )
        }
        devices = list
        occupancies = fresh
    }

    /**
     * 宿舍楼栋号：缓存读不到时主动查一次。
     *
     * 冷启动直接进洗衣房时住宿信息往往还没落缓存，只读缓存会匹配不到任何园区，
     * 于是退到列表第一个（龙河是 301幢洗衣房），和宿舍对不上。
     */
    private suspend fun dormBuilding(): String? {
        AHUCache.getDormitoryInfo()?.building?.takeIf { it.isNotBlank() }?.let { return it }
        val fetched = runCatching { AHURepository.getDormitoryInfo() }.getOrNull()
        return (fetched as? AhuResult.Success)?.value?.building?.takeIf { it.isNotBlank() }
    }

    /**
     * 龙河打开时的机房：宿舍楼栋号匹配 → 上次手选 → 第一个机房。
     * 同一楼栋有多个机房（205栋 / 205幢）时只给候选，不替用户猜。
     */
    private suspend fun resolveLonghePark(parks: List<String>): ParkResolution {
        if (parks.isEmpty()) return ParkResolution(0, emptyList(), null)
        fun remembered(): Int? = parkStore.last(LaundryCampus.LONGHE, _state.value.kind)
            ?.let { name -> parks.indexOfFirst { it == name } }
            ?.takeIf { it >= 0 }

        // 手选优先：切过机房就一直落那个，不被宿舍楼栋掰回去
        remembered()?.let { return ParkResolution(it, emptyList(), null) }

        val building = dormBuilding()
        return when (val matched = QiekjLaundryMatcher.match(building, parks)) {
            is LaundryParkMatch.Hit -> ParkResolution(
                index = matched.index,
                prompt = emptyList(),
                note = building?.let { "已定位：$it" }
            )
            is LaundryParkMatch.Multiple -> ParkResolution(
                index = matched.indices.first(),
                prompt = matched.indices,
                note = building?.let { "$it 有多个洗衣房" }
            )
            LaundryParkMatch.None -> ParkResolution(remembered() ?: 0, emptyList(), null)
        }
    }

    private fun visibleDevices(state: UiState): List<LaundryDevice> =
        devices.filter {
            it.parkIndex == state.parkIndex && it.kind == state.kind
        }

    private fun rebuild() {
        val current = _state.value
        val normalized = normalizeMissingFloorSlots(visibleDevices(current))
        val ordered = orderedDevices(normalized)
        val rows = ordered.map { device -> DeviceRow(device, occupancies[device.id]) }
        _state.value = current.copy(rows = rows, floors = floorsOf(ordered, rows))
    }

    /**
     * 台账偶发单台机器缺失机位号（如兰园A栋 7F 仅有 2 号机和空机位洗衣机）：
     * 若同楼层同方位已有数字机位号，则按 1..N 补齐缺失的最小序号，避免线上台账覆盖后退回显示「洗衣机」。
     *
     * 只对带方位的台账生效：龙河的机位号来自平台机器名，缺号说明名字里本来就没有号，
     * 补一个序号会指向不存在的机位。
     */
    private fun normalizeMissingFloorSlots(devices: List<LaundryDevice>): List<LaundryDevice> {
        val missing = { d: LaundryDevice -> d.slot.isNullOrBlank() && d.floor != null && d.location != null }
        if (devices.none(missing)) return devices
        val usedByGroup = mutableMapOf<Pair<Int, String?>, MutableSet<Int>>()
        devices.forEach { d ->
            val floor = d.floor ?: return@forEach
            val num = d.slot?.toIntOrNull() ?: return@forEach
            usedByGroup.getOrPut(floor to d.location) { mutableSetOf() }.add(num)
        }
        return devices.map { d ->
            val floor = d.floor
            if (!d.slot.isNullOrBlank() || floor == null) return@map d
            val used = usedByGroup[floor to d.location]
            if (used.isNullOrEmpty()) return@map d
            var candidate = 1
            while (candidate in used) candidate++
            used.add(candidate)
            d.copy(slot = candidate.toString())
        }
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

    /** 园区定位结果：[prompt] 非空表示要让用户在候选里选一次。 */
    private data class ParkResolution(val index: Int, val prompt: List<Int>, val note: String?)

    /**
     * 打开时的园区：宿舍楼栋匹配 → 上次手选 → 榴园兜底。
     *
     * 兰园在台账里拆成 A/B/C/D 栋，「兰园2号楼」只能给出多个候选，所以首次进页面
     * 弹一次选择；选过一次就记进 [LaundryParkStore]，以后直接落，不再弹。
     */
    private suspend fun resolvePark(parks: List<String>): ParkResolution {
        if (parks.isEmpty()) return ParkResolution(0, emptyList(), null)
        fun remembered(): Int? = parkStore.last(LaundryCampus.QINGYUAN)
            ?.let { name -> parks.indexOfFirst { it == name } }
            ?.takeIf { it >= 0 }

        val building = dormBuilding()
        return when (val matched = LaundryParkMatcher.match(building, parks)) {
            is LaundryParkMatch.Hit -> ParkResolution(
                index = matched.index,
                prompt = emptyList(),
                note = building?.let { "已定位：$it" }
            )
            is LaundryParkMatch.Multiple -> {
                val last = remembered()
                val keepLast = last != null && last in matched.indices
                val index = if (keepLast) last!! else matched.indices.first()
                ParkResolution(
                    index = index,
                    prompt = if (keepLast) emptyList() else matched.indices,
                    note = building?.let { "$it 有多个洗衣房" }
                )
            }
            LaundryParkMatch.None -> ParkResolution(
                index = remembered() ?: parks.indexOfFirst { it == "榴园" }.takeIf { it >= 0 } ?: 0,
                prompt = emptyList(),
                note = null
            )
        }
    }

    /** 台账升级同一时刻只允许一次下载（20s 超时 + 重试，重复发起没意义还会拖慢首屏）。 */
    private fun startTableUpgrade() {
        if (tableJob?.isActive == true) return
        lastTableCheck = System.currentTimeMillis()
        tableJob = viewModelScope.launch { upgradeTable() }
    }

    /**
     * 台账检查节流：手动刷新/下拉刷新顺带看一眼线上有没有新版本。
     * 之前只在 ViewModel 创建时检查一次，页面常驻就永远拉不到新表。
     */
    private fun maybeUpgradeTable() {
        val now = System.currentTimeMillis()
        if (now - lastTableCheck < TABLE_CHECK_INTERVAL_MS) return
        startTableUpgrade()
    }

    private companion object {
        const val TABLE_CHECK_INTERVAL_MS = 60_000L

        /** 单批回填台数：一批约 1 秒，园区再大也能边查边出。 */
        const val BATCH_SIZE = 16

        /** 龙河相邻两次查询的最小间隔：一次刷新只打 1 个请求，别让手快连点把平台打急。 */
        const val LONGHE_MIN_INTERVAL_MS = 15_000L
    }
}
