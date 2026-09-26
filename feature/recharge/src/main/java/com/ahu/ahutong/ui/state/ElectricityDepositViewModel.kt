package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.core.storage.PaymentKeyboardSetting
import com.ahu.ahutong.core.storage.ElectricityAlertSettings
import com.ahu.ahutong.data.crawler.PayState
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityOptionLevel
import com.ahu.ahutong.data.recharge.ElectricityOptionQuery
import com.ahu.ahutong.data.recharge.ElectricityPayment
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.preset.PresetSuggestions
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltViewModel
class ElectricityDepositViewModel @Inject constructor(
    private val source: ElectricityDepositSource,
    private val behavior: BehaviorRecorder,
    private val presets: PresetSuggestions,
    settings: PaymentKeyboardSetting,
    private val alertSettings: ElectricityAlertSettings
) : ViewModel() {

    /** 支付密码键盘：null 表示设置还没读出来（界面此时不渲染对话框，与迁移前一致）。 */
    val builtInKeyboard: StateFlow<Boolean?> = settings.useBuiltInSecurePasswordKeyboard
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val alertConfiguration: StateFlow<ElectricityAlertConfiguration> = alertSettings.configuration
        .stateIn(viewModelScope, SharingStarted.Eagerly, ElectricityAlertConfiguration())

    fun setAlertEnabled(enabled: Boolean) = saveAlertSetting { alertSettings.setEnabled(enabled) }

    fun addCurrentAlertRoom(thresholdDays: Int = 3) {
        val selection = currentSelection()
        if (!isCompleteSelection(selection) || thresholdDays !in 1..30) return
        val room = ElectricityAlertRoom(selection, thresholdDays)
        // 再次加入同一房间不覆盖已经设置的阈值。
        if (alertConfiguration.value.rooms.any { it.key == room.key }) return
        saveAlertSetting { alertSettings.saveRoom(room) }
    }

    fun updateAlertThreshold(room: ElectricityAlertRoom, thresholdDays: Int) {
        if (thresholdDays !in 1..30) return
        saveAlertSetting { alertSettings.saveRoom(room.copy(thresholdDays = thresholdDays)) }
    }

    fun removeAlertRoom(room: ElectricityAlertRoom) =
        saveAlertSetting { alertSettings.removeRoom(room.key) }

    private fun saveAlertSetting(save: suspend () -> Unit) = viewModelScope.launch {
        try {
            save()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "保存电费预警设置失败，请重试"
        }
    }

    var _payState = MutableStateFlow<PayState>(PayState.Idle)
    val payState : StateFlow<PayState> = _payState

    /** 用户确认了一次缴费：上报发生在 ViewModel 里，界面只发出意图。 */
    fun onPaymentSubmitted() {
        behavior.recordOrganicAction(AppActionId.CONFIRM_ELECTRICITY_PAYMENT)
    }

    fun resetPaymentState() {
        _payState.value = PayState.Idle
    }

    private val _selectedController = MutableStateFlow(source.selectedController())
    val selectedController: StateFlow<ElectricityController> = _selectedController

    private val _campusList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val campusList: StateFlow<List<CampusDataItem>> = _campusList

    private val _selectedCampus = MutableStateFlow<CampusDataItem?>(null)
    val selectedCampus: StateFlow<CampusDataItem?> = _selectedCampus

    private val _buildingsList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val buildingsList: StateFlow<List<CampusDataItem>> = _buildingsList

    private val _selectedBuilding = MutableStateFlow<CampusDataItem?>(null)
    val selectedBuilding: StateFlow<CampusDataItem?> = _selectedBuilding

    private val _floorsList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val floorsList: StateFlow<List<CampusDataItem>> = _floorsList

    private val _selectedFloor = MutableStateFlow<CampusDataItem?>(null)
    val selectedFloor: StateFlow<CampusDataItem?> = _selectedFloor

    private val _roomsList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val roomsList: StateFlow<List<CampusDataItem>> = _roomsList

    private val _selectedRoom = MutableStateFlow<CampusDataItem?>(null)
    val selectedRoom: StateFlow<CampusDataItem?> = _selectedRoom

    private val _fullRoomDetails = MutableStateFlow<ElectricityRoom?>(null)
    val fullRoomDetails: StateFlow<ElectricityRoom?> = _fullRoomDetails

    private val _roomInfo = MutableStateFlow<String?>(null)
    val roomInfo: StateFlow<String?> = _roomInfo

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _historyOptions = MutableStateFlow<List<ElectricityDepositHistoryItem>>(emptyList())
    val historyOptions: StateFlow<List<ElectricityDepositHistoryItem>> = _historyOptions
    private val _presetCandidates = MutableStateFlow<List<PresetCandidate>>(emptyList())
    val presetCandidates: StateFlow<List<PresetCandidate>> = _presetCandidates
    private var activePresetInteraction: PresetInteractionToken? = null
    private var candidatesAtOpportunity: List<PresetCandidate> = emptyList()
    private var selectionLoadJob: Job? = null

    init {
        val history = source.depositHistory()
            .filter { isCompleteSelection(it.selection) }
            .sortedByDescending(ElectricityDepositHistoryItem::updatedAt)
            .distinctBy { selectionKey(it.selection) }
            .take(MAX_ROOM_HISTORY)
        _historyOptions.value = history
        val lastSelection = source.roomSelection()
            ?.takeIf {
                isCompleteSelection(it) &&
                    (it.controller ?: ElectricityController.C) == _selectedController.value
            }
            ?: history.firstOrNull {
                isCompleteSelection(it.selection) &&
                    (it.selection.controller ?: ElectricityController.C) == _selectedController.value
            }?.selection
        if (lastSelection != null) {
            Log.d("ElectricityDepositViewModel", "选择从缓存恢复")
            loadAndRestoreSelection(lastSelection)
        } else {
            fetchInitialOptions()
        }
        viewModelScope.launch {
            _presetCandidates.value = presets.rank(SemanticDomain.ELECTRICITY)
        }
    }

    private fun loadAndRestoreSelection(selection: RoomSelectionInfo, commitPresetOnRoomRequest: Boolean = false) {
        selectionLoadJob?.cancel()
        // 切换后必须重新取得当前房间详情，查询失败时不能沿用上一房间的支付参数。
        _fullRoomDetails.value = null
        _roomInfo.value = null
        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val controller = selection.controller ?: ElectricityController.C
                _selectedController.value = controller
                source.saveController(controller)
                _selectedCampus.value = selection.campus
                _selectedBuilding.value = selection.building
                _selectedFloor.value = selection.floor
                _selectedRoom.value = selection.room
                _campusList.value = listOfNotNull(selection.campus)
                _buildingsList.value = listOfNotNull(selection.building)
                _floorsList.value = listOfNotNull(selection.floor)
                _roomsList.value = listOfNotNull(selection.room)

                // Restore the useful content first. Selector option lists are secondary and should
                // not keep the whole page blocked while a remembered room balance is available.
                val roomDetails = getRoomInfo()
                roomDetails.valueOrNull()?.let {
                    _fullRoomDetails.value = it
                    _roomInfo.value = it.displayInfo
                    persistCurrentSelection()
                    behavior.recordContentState(
                        SemanticDomain.ELECTRICITY,
                        ContentStateBucket.READY,
                        freshnessBucket = 0,
                        resultCount = ResultCountBucket.ONE_TO_FIVE,
                        errorType = ErrorTypeBucket.NONE
                    )
                } ?: throw Exception(
                    roomDetails.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() }
                        ?: "加载房间信息失败"
                )
                _isLoading.value = false

                coroutineScope {
                    val initialOptionsRequest = async { getInitialOptions() }
                    val buildingsRequest = if (controller.requiresCampus) {
                        async { getBuildings() }
                    } else {
                        null
                    }
                    val floorsRequest = async { getFloor() }
                    val roomsRequest = async { getRoom() }

                    initialOptionsRequest.await().valueOrNull()?.let {
                        if (controller.requiresCampus) {
                            _campusList.value = it
                        } else {
                            _buildingsList.value = it
                        }
                    }
                    buildingsRequest?.await()?.valueOrNull()?.let { _buildingsList.value = it }
                    floorsRequest.await().valueOrNull()?.let { _floorsList.value = it }
                    roomsRequest.await().valueOrNull()?.let { _roomsList.value = it }
                }
                if (commitPresetOnRoomRequest) recordRoomPreset()
                Log.d("ElectricityDepositViewModel", "从缓存恢复选择成功")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "恢复选择时发生未知错误"
                Log.e("ElectricityDepositViewModel", "恢复选择失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectHistory(item: ElectricityDepositHistoryItem) {
        loadAndRestoreSelection(item.selection)
    }

    fun restoreRoom(selection: RoomSelectionInfo) {
        if (isCompleteSelection(selection)) loadAndRestoreSelection(selection)
    }

    fun deleteHistory(item: ElectricityDepositHistoryItem) {
        val deletedKey = selectionKey(item.selection)
        val updatedHistory = _historyOptions.value.filterNot {
            selectionKey(it.selection) == deletedKey
        }
        _historyOptions.value = updatedHistory
        source.saveDepositHistory(updatedHistory)
    }

    fun onControllerSelected(controller: ElectricityController) {
        selectionLoadJob?.cancel()
        _selectedController.value = controller
        source.saveController(controller)
        _campusList.value = emptyList()
        _selectedCampus.value = null
        _buildingsList.value = emptyList()
        _selectedBuilding.value = null
        _floorsList.value = emptyList()
        _selectedFloor.value = null
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _fullRoomDetails.value = null
        _roomInfo.value = null
        fetchInitialOptions()
    }

    fun onCampusSelected(campus: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedCampus.value = campus
        _buildingsList.value = emptyList()
        _selectedBuilding.value = null
        _floorsList.value = emptyList()
        _selectedFloor.value = null
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _fullRoomDetails.value = null
        _roomInfo.value = null
        fetchBuildings()
    }

    fun onBuildingSelected(building: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedBuilding.value = building
        _floorsList.value = emptyList()
        _selectedFloor.value = null
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _fullRoomDetails.value = null
        _roomInfo.value = null
        fetchFloor()
    }

    fun onfloorSelected(floor: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedFloor.value = floor
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _fullRoomDetails.value = null
        _roomInfo.value = null
        fetchRoom()
    }

    fun onRoomSelected(room: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedRoom.value = room
        _fullRoomDetails.value = null
        _roomInfo.value = null
        fetchRoomInfo()
    }

    fun retry() {
        when {
            _selectedController.value.requiresCampus && _selectedCampus.value == null -> fetchInitialOptions()
            _selectedBuilding.value == null && _selectedController.value.requiresCampus -> fetchBuildings()
            _selectedBuilding.value == null -> fetchInitialOptions()
            _selectedFloor.value == null -> fetchFloor()
            _selectedRoom.value == null -> fetchRoom()
            else -> fetchRoomInfo()
        }
    }

    private fun fetchInitialOptions() {
        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getInitialOptions()
                val items = response.valueOrNull()
                if (items != null) {
                    if (_selectedController.value.requiresCampus) {
                        _campusList.value = items
                        if (_selectedCampus.value == null) {
                            items.firstOrNull()?.let { first ->
                                _selectedCampus.value = first
                                fetchBuildings()
                            }
                        }
                    } else {
                        _buildingsList.value = items
                    }
                } else {
                    _errorMessage.value = response.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() } ?: "加载电控选项失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedCampus.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getInitialOptions(): AhuResult<List<CampusDataItem>> {
        return source.options(optionQuery(ElectricityOptionLevel.Initial))
    }

    private fun fetchBuildings() {
        if (_selectedCampus.value == null) {
            _errorMessage.value = "请先选择一个校区"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getBuildings()
                val items = response.valueOrNull()
                if (items != null) {
                    _buildingsList.value = items
                } else {
                    _errorMessage.value = response.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() } ?: "加载楼栋失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedBuilding.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getBuildings(): AhuResult<List<CampusDataItem>> {
        return source.options(optionQuery(ElectricityOptionLevel.Buildings))
    }

    private fun fetchFloor() {
        if (_selectedBuilding.value == null) {
            _errorMessage.value = "请先选择一个楼栋"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getFloor()
                val items = response.valueOrNull()
                if (items != null) {
                    _floorsList.value = items
                } else {
                    _errorMessage.value = response.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() } ?: "加载楼层失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedFloor.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getFloor(): AhuResult<List<CampusDataItem>> {
        return source.options(optionQuery(ElectricityOptionLevel.Floors))
    }

    private fun fetchRoom() {
        if (_selectedFloor.value == null) {
            _errorMessage.value = "请先选择一个楼层"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getRoom()
                val items = response.valueOrNull()
                if (items != null) {
                    _roomsList.value = items
                } else {
                    _errorMessage.value = response.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() } ?: "加载房间失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedRoom.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getRoom(): AhuResult<List<CampusDataItem>> {
        return source.options(optionQuery(ElectricityOptionLevel.Rooms))
    }

    private fun fetchRoomInfo() {
        if (_selectedRoom.value == null) {
            _errorMessage.value = "请先选择一个房间"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getRoomInfo()
                val details = response.valueOrNull()
                if (details != null) {
                    _fullRoomDetails.value = details
                    _roomInfo.value = details.displayInfo
                    persistCurrentSelection()
                    behavior.recordContentState(
                        SemanticDomain.ELECTRICITY,
                        ContentStateBucket.READY,
                        freshnessBucket = 0,
                        resultCount = ResultCountBucket.ONE_TO_FIVE,
                        errorType = ErrorTypeBucket.NONE
                    )
                    launch { recordRoomPreset() }
                } else {
                    _errorMessage.value = response.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() } ?: "加载房间信息失败"
                    reportRoomError()
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
                reportRoomError()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun applyPresetCandidate(candidate: PresetCandidate) = viewModelScope.launch {
        val applied = presets.apply(candidate) ?: return@launch
        activePresetInteraction = applied.interactionToken
        candidatesAtOpportunity = _presetCandidates.value
        val selection = runCatching { Gson().fromJson(applied.localPayloadJson, RoomSelectionInfo::class.java) }.getOrNull()
            ?: return@launch
        val controller = selection.controller ?: ElectricityController.C
        if (
            selection.building == null ||
            selection.floor == null ||
            selection.room == null ||
            controller.requiresCampus && selection.campus == null
        ) {
            return@launch
        }
        _presetCandidates.value = emptyList()
        loadAndRestoreSelection(selection, commitPresetOnRoomRequest = true)
    }

    private suspend fun recordRoomPreset() {
        val selection = RoomSelectionInfo(
            campus = _selectedCampus.value,
            building = _selectedBuilding.value,
            floor = _selectedFloor.value,
            room = _selectedRoom.value,
            controller = _selectedController.value
        )
        val controller = selection.controller ?: ElectricityController.C
        val campus = selection.campus
        val building = selection.building ?: return
        val floor = selection.floor ?: return
        val room = selection.room ?: return
        if (controller.requiresCampus && campus == null) return
        presets.recordNaturalSubmission(
            PresetSubmission(
                SemanticDomain.ELECTRICITY,
                Gson().toJson(selection),
                "{\"roomCategory\":\"RECENT_LOCAL_ROOM\"}",
                listOf(
                    controller.name,
                    campus?.value.orEmpty(),
                    building.value,
                    floor.value,
                    room.value
                ).joinToString("|")
            ),
            interactionToken = activePresetInteraction,
            candidatesAtOpportunity = candidatesAtOpportunity.ifEmpty { _presetCandidates.value }
        )
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
        _presetCandidates.value = presets.rank(SemanticDomain.ELECTRICITY)
    }

    fun onPresetCandidateVisible(candidate: PresetCandidate) = viewModelScope.launch {
        val token = presets.markExposed(candidate) ?: return@launch
        activePresetInteraction = token
        candidatesAtOpportunity = _presetCandidates.value
    }

    fun onPresetSurfaceDisposed() {
        presets.expire(activePresetInteraction)
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
    }

    override fun onCleared() {
        onPresetSurfaceDisposed()
        super.onCleared()
    }

    private fun reportRoomError() {
        behavior.recordContentState(
            SemanticDomain.ELECTRICITY,
            ContentStateBucket.ERROR,
            freshnessBucket = 7,
            resultCount = ResultCountBucket.ZERO,
            errorType = ErrorTypeBucket.NETWORK
        )
    }

    private suspend fun getRoomInfo(): AhuResult<ElectricityRoom> =
        source.room(_selectedController.value, currentSelection())

    fun pay(amount: String, password: String) {
        if (amount.toDoubleOrNull() ?: 0.0 <= 0) {
            _errorMessage.value = "请输入有效金额"
            return
        }
        if (password.length != 6) {
            _errorMessage.value = "请输入6位密码"
            return
        }

        _payState.value = PayState.InProgress
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d("ElectricityDepositViewModel", "开始支付流程（金额与凭证已隐藏）")
                val room = _fullRoomDetails.value?.details
                if (room == null) {
                    val message = "房间详细信息为空，无法支付"
                    _errorMessage.value = message
                    _payState.value = PayState.Failed(message)
                    return@launch
                }
                val result = source.pay(
                    ElectricityPayment(
                        controller = _selectedController.value,
                        amount = amount,
                        password = password,
                        room = room
                    )
                )
                val receipt = result.valueOrNull()
                if (receipt == null) {
                    val message = result.errorOrNull()?.toUserMessage()?.takeIf { it.isNotBlank() }
                        ?: "创建订单失败"
                    _errorMessage.value = message
                    _payState.value = PayState.Failed(message)
                    return@launch
                }

                val orderId = receipt.reference
                _errorMessage.value = null
                _payState.value = PayState.Succeeded(orderId)
                Log.d("ElectricityDepositViewModel", "支付成功!")
                val chargeAmount = amount.toDoubleOrNull()
                if (chargeAmount != null && chargeAmount > 0) {
                    val existingInfo = source.chargeInfo()
                    if (existingInfo == null) {
                        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                        source.saveChargeInfo(
                            ElectricityChargeInfo(
                                totalAmount = chargeAmount,
                                firstChargeDate = dateFormat.format(Date())
                            )
                        )
                    } else {
                        source.saveChargeInfo(
                            existingInfo.copy(totalAmount = existingInfo.totalAmount + chargeAmount)
                        )
                    }
                }
                persistCurrentSelection(selection = currentSelection(), confirmedByPayment = true)
                delay(1_000L)
                getRoomInfo().valueOrNull()?.let { refreshed ->
                    _fullRoomDetails.value = refreshed
                    _roomInfo.value = refreshed.displayInfo
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = "支付请求异常: ${e.message}"
                _errorMessage.value = errorMessage
                _payState.value = PayState.Failed(errorMessage)
                Log.e("ElectricityDepositViewModel", "支付请求异常")
            } finally {
                _isLoading.value = false
                Log.d("ElectricityDepositViewModel", "支付流程结束。")
            }
        }
    }

    private fun optionQuery(level: ElectricityOptionLevel) = ElectricityOptionQuery(
        controller = _selectedController.value,
        selection = currentSelection(),
        level = level
    )

    private fun currentSelection() = RoomSelectionInfo(
        campus = _selectedCampus.value,
        building = _selectedBuilding.value,
        floor = _selectedFloor.value,
        room = _selectedRoom.value,
        controller = _selectedController.value
    )

    private fun isCompleteSelection(selection: RoomSelectionInfo): Boolean {
        val controller = selection.controller ?: ElectricityController.C
        return (!controller.requiresCampus || selection.campus != null) &&
            selection.building != null &&
            selection.floor != null &&
            selection.room != null
    }

    private fun persistCurrentSelection(
        selection: RoomSelectionInfo = currentSelection(),
        confirmedByPayment: Boolean = false
    ) {
        if (!isCompleteSelection(selection)) return

        source.saveRoomSelection(selection)
        val roomLabel = normalizeLabel(
            _fullRoomDetails.value?.details?.roomName ?: selection.room?.name.orEmpty()
        )
        if (roomLabel.isBlank()) return
        val controller = selection.controller ?: ElectricityController.C
        val label = "${controller.displayName} · $roomLabel"

        val key = selectionKey(selection)
        val previous = _historyOptions.value.firstOrNull { selectionKey(it.selection) == key }
        val item = ElectricityDepositHistoryItem(
            selection = selection,
            label = label,
            updatedAt = System.currentTimeMillis(),
            confirmedByPayment = confirmedByPayment || previous?.confirmedByPayment == true
        )
        val updatedHistory = (listOf(item) + _historyOptions.value.filter {
            selectionKey(it.selection) != key
        }).take(MAX_ROOM_HISTORY)
        _historyOptions.value = updatedHistory
        source.saveDepositHistory(updatedHistory)
    }

    private fun selectionKey(selection: RoomSelectionInfo): String {
        return listOf(
            (selection.controller ?: ElectricityController.C).name,
            selection.campus?.value,
            selection.building?.value,
            selection.floor?.value,
            selection.room?.value
        ).joinToString("|") { it ?: "" }
    }

    private fun normalizeLabel(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("房间：")) {
            value = value.removePrefix("房间：").trim()
        }
        val parts = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "" else parts.last()
    }

    private companion object {
        const val MAX_ROOM_HISTORY = 12
    }
}
