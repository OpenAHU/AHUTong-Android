package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.ui.components.GlassCard
import com.ahu.ahutong.ui.components.SecondaryPageScaffold
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.CampusDataItem
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ElectricityDeposit(
    viewModel: ElectricityDepositViewModel = hiltViewModel()
) {
    DisposableEffect(viewModel) {
        onDispose { viewModel.onPresetSurfaceDisposed() }
    }
    val behaviorReporter = rememberBehaviorActionReporter()
    val payState = viewModel.payState.collectAsState()
    LaunchedEffect(payState.value) {
        when (payState.value) {
            is PayState.Succeeded -> {
                // 成功态展示订单号的时间拉长一些，方便看清
                delay(2400)
                viewModel.resetPaymentState()
            }

            is PayState.Failed -> {
                delay(1000)
                viewModel.resetPaymentState()
            }

            else -> {

            }
        }
    }

    val focusManager = LocalFocusManager.current
    val campusList by viewModel.campusList.collectAsState()
    val selectedCampus by viewModel.selectedCampus.collectAsState()

    val buildingsList by viewModel.buildingsList.collectAsState()
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()

    val floorsList by viewModel.floorsList.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()

    val roomsList by viewModel.roomsList.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()

    val roomInfo by viewModel.roomInfo.collectAsState()
    val historyOptions by viewModel.historyOptions.collectAsState()
    val presetCandidates by viewModel.presetCandidates.collectAsState()

    var campusDropdownExpanded by remember { mutableStateOf(false) }
    var buildingsDropdownExpanded by remember { mutableStateOf(false) }
    var floorsDropdownExpanded by remember { mutableStateOf(false) }
    var roomsDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var infoClickCount by remember { mutableStateOf(0) }
    var currentToast by remember { mutableStateOf<Toast?>(null) }
    fun showToast(msg: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT).also { it.show() }
    }
    fun validateBefore(level: Int): Boolean {
        val msg = when {
            level >= 1 && selectedCampus == null -> "请先选择校区"
            level >= 2 && selectedBuilding == null -> "请先选择楼栋"
            level >= 3 && selectedFloor == null -> "请先选择楼层"
            else -> null
        }
        return if (msg != null) {
            showToast(msg)
            false
        } else true
    }

    val openBuildingMenu = { if (validateBefore(1)) buildingsDropdownExpanded = true }
    val openFloorMenu = { if (validateBefore(2)) floorsDropdownExpanded = true }
    val openRoomMenu = { if (validateBefore(3)) roomsDropdownExpanded = true }

    var showResetDialog by remember { mutableStateOf(false) }

    var amount by remember { mutableStateOf("") }

    var showDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val onConfirmClick: () -> Unit = {
        when {
            selectedCampus == null -> showToast("请先选择校区")
            selectedBuilding == null -> showToast("请先选择楼栋")
            selectedFloor == null -> showToast("请先选择楼层")
            selectedRoom == null -> showToast("请先选择房间")
            amount.isBlank() -> showToast("请输入缴费金额")
            (amount.toDoubleOrNull() ?: 0.0) <= 0.0 -> showToast("请输入有效金额")
            else -> showDialog = true
        }
    }

    if (isRadiantUi) {
        SecondaryPageScaffold(
            title = "电控缴费",
            content = {
                ElectricityFormBody(
                    campusList = campusList, selectedCampus = selectedCampus,
                    campusDropdownExpanded = campusDropdownExpanded,
                    onCampusDropdownChange = { campusDropdownExpanded = it },
                    onCampusSelect = { campus ->
                        viewModel.onCampusSelected(campus)
                        campusDropdownExpanded = false
                    },
                    buildingsList = buildingsList, selectedBuilding = selectedBuilding,
                    buildingsDropdownExpanded = buildingsDropdownExpanded,
                    onBuildingDropdownChange = { if (it) openBuildingMenu() else buildingsDropdownExpanded = false },
                    onBuildingSelect = { b ->
                        viewModel.onBuildingSelected(b)
                        buildingsDropdownExpanded = false
                    },
                    floorsList = floorsList, selectedFloor = selectedFloor,
                    floorsDropdownExpanded = floorsDropdownExpanded,
                    onFloorDropdownChange = { if (it) openFloorMenu() else floorsDropdownExpanded = false },
                    onFloorSelect = { f ->
                        viewModel.onfloorSelected(f)
                        floorsDropdownExpanded = false
                    },
                    roomsList = roomsList, selectedRoom = selectedRoom,
                    roomsDropdownExpanded = roomsDropdownExpanded,
                    onRoomDropdownChange = { if (it) openRoomMenu() else roomsDropdownExpanded = false },
                    onRoomSelect = { r ->
                        viewModel.onRoomSelected(r)
                        roomsDropdownExpanded = false
                    },
                    historyOptions = historyOptions,
                    roomInfo = roomInfo,
                    presetCandidates = presetCandidates,
                    onPresetVisible = { viewModel.onPresetCandidateVisible(it) },
                    onPresetApply = { viewModel.applyPresetCandidate(it) },
                    onHistorySelect = { viewModel.selectHistory(it) },
                    onInfoClick = {
                        infoClickCount++
                        currentToast?.cancel()
                        val message = when {
                            infoClickCount == 1 -> "点击五次查看累计充值记录，长按清空记录"
                            infoClickCount == 2 -> "再点击三次即可查看累计充值记录"
                            infoClickCount == 3 -> "再点击两次即可查看累计充值记录"
                            infoClickCount == 4 -> "再点击一次即可查看累计充值记录"
                            infoClickCount >= 5 -> {
                                val chargeInfo = AHUCache.getElectricityChargeInfo()
                                if (chargeInfo != null) {
                                    "从${chargeInfo.firstChargeDate}起累计电费充值金额为：${
                                        "%.2f".format(
                                            chargeInfo.totalAmount
                                        )
                                    }元"
                                } else {
                                    "暂无充值记录"
                                }
                            }

                            else -> null
                        }
                        if (message != null) {
                            val toastLength =
                                if (infoClickCount >= 5) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            val newToast = Toast.makeText(context, message, toastLength)
                            newToast.show()
                            currentToast = newToast
                        }
                    },
                    onInfoLongClick = { showResetDialog = true },
                    amount = amount,
                    onAmountChange = { newText ->
                        if (newText.isEmpty()) {
                            amount = newText
                            return@ElectricityFormBody
                        }
                        val regex = Regex("^\\d*\\.?\\d{0,2}$")
                        if (regex.matches(newText)) {
                            amount = newText
                        }
                    },
                    onClearFocus = { focusManager.clearFocus() }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .clip(SmoothRoundedCornerShape(32.dp))
                            .background(
                                animateColorAsState(
                                    targetValue = when (payState.value) {
                                        is PayState.Idle -> 90.a1 withNight 85.a1
                                        is PayState.InProgress -> 70.a1 withNight 60.a1
                                        is PayState.Failed -> Color.Red
                                        is PayState.Succeeded -> 70.a1 withNight 60.a1
                                    }
                                ).value
                            )
                            .animateContentSize(spring(stiffness = Spring.StiffnessLow))
                    ) {
                        when (payState.value) {
                            is PayState.Idle -> {
                                Text(
                                    text = "确认",
                                    modifier = Modifier
                                        .clickable(
                                            role = Role.Button,
                                            onClick = onConfirmClick
                                        )
                                        .padding(24.dp, 16.dp),
                                    color = 0.n1,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            is PayState.InProgress -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = 100.n1,
                                        strokeWidth = 6.dp
                                    )
                                    Text(
                                        text = "支付中...",
                                        modifier = Modifier.padding(4.dp),
                                        color = 100.n1,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            is PayState.Succeeded -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = 100.n1
                                    )
                                    Text(
                                        text = "支付成功！ 订单号：${(payState.value as PayState.Succeeded).message}",
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .clickable {

                                            },
                                        color = 100.n1,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }

                            is PayState.Failed -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = 100.n1
                                    )
                                    Text(
                                        text = "支付失败！",
                                        modifier = Modifier.padding(4.dp),
                                        color = 100.n1,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "电控缴费",
                modifier = Modifier.padding(24.dp, 32.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            ElectricityFormBody(
                campusList = campusList, selectedCampus = selectedCampus,
                campusDropdownExpanded = campusDropdownExpanded,
                onCampusDropdownChange = { campusDropdownExpanded = it },
                onCampusSelect = { campus ->
                    viewModel.onCampusSelected(campus)
                    campusDropdownExpanded = false
                },
                buildingsList = buildingsList, selectedBuilding = selectedBuilding,
                buildingsDropdownExpanded = buildingsDropdownExpanded,
                onBuildingDropdownChange = { if (it) openBuildingMenu() else buildingsDropdownExpanded = false },
                onBuildingSelect = { b ->
                    viewModel.onBuildingSelected(b)
                    buildingsDropdownExpanded = false
                },
                floorsList = floorsList, selectedFloor = selectedFloor,
                floorsDropdownExpanded = floorsDropdownExpanded,
                onFloorDropdownChange = { if (it) openFloorMenu() else floorsDropdownExpanded = false },
                onFloorSelect = { f ->
                    viewModel.onfloorSelected(f)
                    floorsDropdownExpanded = false
                },
                roomsList = roomsList, selectedRoom = selectedRoom,
                roomsDropdownExpanded = roomsDropdownExpanded,
                onRoomDropdownChange = { if (it) openRoomMenu() else roomsDropdownExpanded = false },
                onRoomSelect = { r ->
                    viewModel.onRoomSelected(r)
                    roomsDropdownExpanded = false
                },
                historyOptions = historyOptions,
                roomInfo = roomInfo,
                presetCandidates = presetCandidates,
                onPresetVisible = { viewModel.onPresetCandidateVisible(it) },
                onPresetApply = { viewModel.applyPresetCandidate(it) },
                onHistorySelect = { viewModel.selectHistory(it) },
                onInfoClick = {
                    infoClickCount++
                    currentToast?.cancel()
                    val message = when {
                        infoClickCount == 1 -> "点击五次查看累计充值记录，长按清空记录"
                        infoClickCount == 2 -> "再点击三次即可查看累计充值记录"
                        infoClickCount == 3 -> "再点击两次即可查看累计充值记录"
                        infoClickCount == 4 -> "再点击一次即可查看累计充值记录"
                        infoClickCount >= 5 -> {
                            val chargeInfo = AHUCache.getElectricityChargeInfo()
                            if (chargeInfo != null) {
                                "从${chargeInfo.firstChargeDate}起累计电费充值金额为：${
                                    "%.2f".format(
                                        chargeInfo.totalAmount
                                    )
                                }元"
                            } else {
                                "暂无充值记录"
                            }
                        }

                        else -> null
                    }
                    if (message != null) {
                        val toastLength =
                            if (infoClickCount >= 5) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                        val newToast = Toast.makeText(context, message, toastLength)
                        newToast.show()
                        currentToast = newToast
                    }
                },
                onInfoLongClick = { showResetDialog = true },
                amount = amount,
                onAmountChange = { newText ->
                    if (newText.isEmpty()) {
                        amount = newText
                        return@ElectricityFormBody
                    }
                    val regex = Regex("^\\d*\\.?\\d{0,2}$")
                    if (regex.matches(newText)) {
                        amount = newText
                    }
                },
                onClearFocus = { focusManager.clearFocus() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .clip(SmoothRoundedCornerShape(32.dp))
                        .background(
                            animateColorAsState(
                                targetValue = when (payState.value) {
                                    is PayState.Idle -> 90.a1 withNight 85.a1
                                    is PayState.InProgress -> 70.a1 withNight 60.a1
                                    is PayState.Failed -> Color.Red
                                    is PayState.Succeeded -> 70.a1 withNight 60.a1
                                }
                            ).value
                        )
                        .animateContentSize(spring(stiffness = Spring.StiffnessLow))
                ) {
                    when (payState.value) {
                        is PayState.Idle -> {
                            Text(
                                text = "确认",
                                modifier = Modifier
                                    .clickable(
                                        role = Role.Button,
                                        onClick = onConfirmClick
                                    )
                                    .padding(24.dp, 16.dp),
                                color = 0.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        is PayState.InProgress -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = 100.n1,
                                    strokeWidth = 6.dp
                                )
                                Text(
                                    text = "支付中...",
                                    modifier = Modifier.padding(4.dp),
                                    color = 100.n1,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        is PayState.Succeeded -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = 100.n1
                                )
                                Text(
                                    text = "支付成功！ 订单号：${(payState.value as PayState.Succeeded).message}",
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clickable {

                                        },
                                    color = 100.n1,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }

                        is PayState.Failed -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = 100.n1
                                )
                                Text(
                                    text = "支付失败！",
                                    modifier = Modifier.padding(4.dp),
                                    color = 100.n1,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            containerColor = 100.n1 withNight 20.n1,
            textContentColor = 10.n1 withNight 90.n1,
            onDismissRequest = { showDialog = false },
            title = { Text("请输入校园卡密码", color = 10.n1 withNight 90.n1) },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                password = input
                                errorMsg = null
                            }
                        },
                        label = { Text("密码 (6位数字)", color = 40.n1 withNight 60.n1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = errorMsg != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = 10.n1 withNight 90.n1,
                            unfocusedTextColor = 10.n1 withNight 90.n1,
                            focusedBorderColor = 20.n1 withNight 80.n1
                        )
                    )
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (password.length == 6) {
                        showDialog = false
                        behaviorReporter.organic(AppActionId.CONFIRM_ELECTRICITY_PAYMENT)
                        viewModel.pay(amount, password)
                    } else {
                        errorMsg = "密码必须是6位数字"
                    }
                }) {
                    Text("确认", color = 10.n1 withNight 90.n1)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    password = ""
                    errorMsg = null
                }) {
                    Text("取消", color = 10.n1 withNight 90.n1)
                }
            }
        )
    }
    if (showResetDialog) {
        AlertDialog(
            containerColor = 100.n1 withNight 20.n1,
            titleContentColor = 10.n1 withNight 90.n1,
            textContentColor = 40.n1 withNight 70.n1,
            onDismissRequest = { showResetDialog = false },
            title = { Text("确认操作") },
            text = { Text("您确定要将累计充值金额清零吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AHUCache.clearElectricityChargeInfo()
                        Toast.makeText(context, "累计记录已清零", Toast.LENGTH_SHORT).show()
                        showResetDialog = false
                    }
                ) {
                    Text("确认", color = 40.a1 withNight 80.a1)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text("取消", color = 40.a1 withNight 80.a1)
                }
            }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
@Composable
private fun ElectricityFormBody(
    campusList: List<CampusDataItem>,
    selectedCampus: CampusDataItem?,
    campusDropdownExpanded: Boolean,
    onCampusDropdownChange: (Boolean) -> Unit,
    onCampusSelect: (CampusDataItem) -> Unit,
    buildingsList: List<CampusDataItem>,
    selectedBuilding: CampusDataItem?,
    buildingsDropdownExpanded: Boolean,
    onBuildingDropdownChange: (Boolean) -> Unit,
    onBuildingSelect: (CampusDataItem) -> Unit,
    floorsList: List<CampusDataItem>,
    selectedFloor: CampusDataItem?,
    floorsDropdownExpanded: Boolean,
    onFloorDropdownChange: (Boolean) -> Unit,
    onFloorSelect: (CampusDataItem) -> Unit,
    roomsList: List<CampusDataItem>,
    selectedRoom: CampusDataItem?,
    roomsDropdownExpanded: Boolean,
    onRoomDropdownChange: (Boolean) -> Unit,
    onRoomSelect: (CampusDataItem) -> Unit,
    historyOptions: List<ElectricityDepositHistoryItem>,
    roomInfo: String?,
    presetCandidates: List<PresetCandidate>,
    onPresetVisible: (PresetCandidate) -> Unit,
    onPresetApply: (PresetCandidate) -> Unit,
    onHistorySelect: (ElectricityDepositHistoryItem) -> Unit,
    onInfoClick: () -> Unit,
    onInfoLongClick: () -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    onClearFocus: () -> Unit
) {
    // RadiantUI 脚手架已自带 16dp 水平边距，经典分支需自行补齐，保证两分支内容逐像素一致
    val hPadding = if (isRadiantUi) 0.dp else 16.dp

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // 使用最近房间（个性化预设）
        presetCandidates.firstOrNull()?.let { candidate ->
            LaunchedEffect(candidate.opportunityId, candidate.presetId) {
                onPresetVisible(candidate)
            }
            Text(
                text = "使用最近房间",
                modifier = Modifier
                    .padding(horizontal = hPadding)
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(90.a1 withNight 30.n1)
                    .clickable { onPresetApply(candidate) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = 10.n1 withNight 90.n1,
                style = MaterialTheme.typography.titleMedium
            )
        }

        GlassCard(
            containerColor = 100.n1 withNight 20.n1,
            modifier = Modifier
                .padding(horizontal = hPadding)
                .fillMaxWidth()
        ) {
            Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { onCampusDropdownChange(true) },
            ) {
                Text(text = "选择校区", style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCampusDropdownChange(true) }
                ) {
                    Text(text = selectedCampus?.name ?: "请选择校区")
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "展开校区列表")

                    DropdownMenu(
                        expanded = campusDropdownExpanded,
                        modifier = Modifier.heightIn(max = 350.dp).background(99.n1 withNight 10.n1),
                        onDismissRequest = { onCampusDropdownChange(false) },
                    ) {
                        campusList.forEach { campus ->
                            DropdownMenuItem(
                                text = { Text(campus.name, color = 10.n1 withNight 90.n1) },
                                onClick = { onCampusSelect(campus) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { onBuildingDropdownChange(true) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "选择楼栋", style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onBuildingDropdownChange(true) }
                ) {
                    Text(text = selectedBuilding?.name ?: "请选择楼栋")
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "展开楼栋列表")

                    DropdownMenu(
                        expanded = buildingsDropdownExpanded,
                        modifier = Modifier.heightIn(max = 450.dp).background(99.n1 withNight 10.n1),
                        onDismissRequest = { onBuildingDropdownChange(false) },
                    ) {
                        buildingsList.forEach { building ->
                            DropdownMenuItem(
                                text = { Text(building.name, color = 10.n1 withNight 90.n1) },
                                onClick = { onBuildingSelect(building) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { onFloorDropdownChange(true) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "选择楼层", style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onFloorDropdownChange(true) },
                ) {
                    Text(text = selectedFloor?.name ?: "请选择楼层")
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "展开楼层列表")

                    DropdownMenu(
                        expanded = floorsDropdownExpanded,
                        modifier = Modifier.heightIn(max = 450.dp).background(99.n1 withNight 10.n1),
                        onDismissRequest = { onFloorDropdownChange(false) },
                    ) {
                        floorsList.forEach { floor ->
                            DropdownMenuItem(
                                text = { Text(floor.name, color = 10.n1 withNight 90.n1) },
                                onClick = { onFloorSelect(floor) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { onRoomDropdownChange(true) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "选择房间", style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRoomDropdownChange(true) },
                ) {
                    Text(text = selectedRoom?.name ?: "请选择房间")
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "展开房间列表")

                    DropdownMenu(
                        expanded = roomsDropdownExpanded,
                        onDismissRequest = { onRoomDropdownChange(false) },
                        modifier = Modifier.heightIn(max = 500.dp).background(99.n1 withNight 10.n1)
                    ) {
                        roomsList.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(room.name, color = 10.n1 withNight 90.n1) },
                                onClick = { onRoomSelect(room) }
                            )
                        }
                    }
                }
            }

            // 上次充值记录快捷入口（仅当有历史时）
            if (historyOptions.size == 2) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    historyOptions.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = item.label,
                                modifier = Modifier
                                    .clip(SmoothRoundedCornerShape(16.dp))
                                    .background(90.a1 withNight 30.n1)
                                    .padding(8.dp)
                                    .clickable { onHistorySelect(item) },
                                color = 10.n1 withNight 90.n1,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onInfoClick,
                        onLongClick = onInfoLongClick
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "信息", style = MaterialTheme.typography.titleMedium)
                Text(text = roomInfo?.replace("，", "\n") ?: "")
            }
            }
        }

        GlassCard(
            containerColor = 100.n1 withNight 20.n1,
            modifier = Modifier
                .padding(horizontal = hPadding)
                .fillMaxWidth(),
        ) {
            Column {
            Text(
                text = "缴费金额",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            TextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = { Text("请输入金额", color = 30.n1 withNight 70.n1) },
                textStyle = TextStyle(fontSize = 16.sp, color = 10.n1 withNight 90.n1),

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onClearFocus() }
                ),
                singleLine = true
            )
            }
        }
    }
}