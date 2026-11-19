package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import com.ahu.ahutong.ui.haptics.HapticIconButton as IconButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.ui.component.SecurePaymentPasswordDialog
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppComponentTokens
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.delay

@Composable
fun ElectricityDeposit(
    onBack: () -> Unit,
    onOpenRecentRooms: () -> Unit,
    onOpenAlertSettings: () -> Unit,
    viewModel: ElectricityDepositViewModel = hiltViewModel(),
    initialSelection: RoomSelectionInfo? = null,
    onInitialSelectionConsumed: () -> Unit = {}
) {
    val payState by viewModel.payState.collectAsState()
    val builtInKeyboard by viewModel.builtInKeyboard.collectAsState()
    val selectedController by viewModel.selectedController.collectAsState()
    val selectedCampus by viewModel.selectedCampus.collectAsState()
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()
    val fullRoomDetails by viewModel.fullRoomDetails.collectAsState()
    val roomInfo by viewModel.roomInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val historyOptions by viewModel.historyOptions.collectAsState()
    val alertConfiguration by viewModel.alertConfiguration.collectAsState()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var amount by rememberSaveable { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialSelection) {
        initialSelection?.let {
            viewModel.restoreRoom(it)
            onInitialSelectionConsumed()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val clearPassword = {
            password = ""
            passwordError = null
            showPasswordDialog = false
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) clearPassword()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clearPassword()
        }
    }
    LaunchedEffect(payState) {
        if (payState is PayState.Succeeded || payState is PayState.Failed) {
            delay(PAYMENT_RESULT_DISPLAY_DURATION_MS)
            viewModel.resetPaymentState()
        }
    }

    val canPay = (!selectedController.requiresCampus || selectedCampus != null) &&
        selectedBuilding != null && selectedFloor != null && selectedRoom != null &&
        fullRoomDetails != null &&
        amount.toDoubleOrNull()?.let { it > 0.0 } == true &&
        !isLoading && payState is PayState.Idle
    // 壳（AppPageScaffold）已统一提供内容水平 padding，页面侧补偿归零；阶段④清理页面内容时再删

    val pageContent: @Composable ColumnScope.() -> Unit = {
        if (errorMessage != null) {
            AppStateCard.InlineError(
                horizontalInset = 0.dp,
                title = "电控信息加载失败",
                message = errorMessage.orEmpty(),
                retryLabel = "重新加载",
                onRetry = viewModel::retry
            )
        }

        if (historyOptions.isNotEmpty()) {
            AppButton(
                onClick = onOpenRecentRooms,
                modifier = Modifier
                    .fillMaxWidth(),
                enabled = !isLoading,
                variant = AppButtonVariant.Secondary
            ) {
                Text("最近使用的房间")
            }
        }

        ElectricityRoomSelectors(viewModel)

        roomInfo?.takeIf(String::isNotBlank)?.let { info ->
            val roomContent: @Composable ColumnScope.() -> Unit = {
                Text(
                    text = "房间信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = info.replace("，", "\n"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            AppSectionCard(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = roomContent
            )
        }

        val amountContent: @Composable ColumnScope.() -> Unit = {
            Text(
                text = "缴费金额",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            AppTextField(
                value = amount,
                onValueChange = { input ->
                    if (input.isEmpty() || Regex("^\\d*\\.?\\d{0,2}$").matches(input)) {
                        amount = input
                    }
                },
                label = "金额（元）",
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }
        AppSectionCard(content = amountContent)

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = payState) {
                PayState.Idle -> Unit
                PayState.InProgress -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    Text("正在提交缴费", style = MaterialTheme.typography.bodyLarge)
                }
                is PayState.Succeeded -> Text(
                    text = "缴费成功，订单号：${state.message}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                is PayState.Failed -> Text(
                    text = "缴费失败：${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            AppButton(
                onClick = {
                    password = ""
                    passwordError = null
                    showPasswordDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canPay
            ) {
                Text(if (payState is PayState.InProgress) "正在支付" else "确认缴费")
            }
        }
    }

    AppPageScaffold(
        title = "电控缴费",
        onBack = onBack,
        actions = listOf(
            TrailingAction(
                icon = if (alertConfiguration.enabled) Icons.Outlined.NotificationsActive
                    else Icons.Outlined.NotificationsNone,
                contentDescription = "电费预警设置",
                onClick = onOpenAlertSettings
            )
        ),
        modifier = Modifier.fillMaxSize(),
        bottomPadding = 48.dp,
        content = pageContent
    )

    if (showPasswordDialog) {
        SecurePaymentPasswordDialog(
            password = password,
            onPasswordChange = {
                password = it
                passwordError = null
            },
            title = "请输入校园卡密码",
            errorMessage = passwordError,
            useBuiltInKeyboard = builtInKeyboard,
            onDismissRequest = {
                showPasswordDialog = false
                password = ""
                passwordError = null
            },
            onConfirm = { confirmedPassword ->
                if (confirmedPassword.length == 6) {
                    showPasswordDialog = false
                    password = ""
                    passwordError = null
                    viewModel.onPaymentSubmitted()
                    viewModel.pay(amount, confirmedPassword)
                } else {
                    passwordError = "密码必须是 6 位数字"
                }
            }
        )
    }
}

@Composable
fun ElectricityRecentRooms(
    onBack: () -> Unit,
    onRoomSelected: () -> Unit,
    viewModel: ElectricityDepositViewModel
) {
    val historyOptions by viewModel.historyOptions.collectAsState()
    AppPageScaffold(
        title = "最近使用的房间",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        bottomPadding = 48.dp,
        content = {
            if (historyOptions.isEmpty()) {
                AppStateCard.Empty(message = "暂无最近使用的房间")
            } else {
                historyOptions.forEach { item ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.selectHistory(item)
                            onRoomSelected()
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(item.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = listOfNotNull(
                                        item.selection.campus?.name,
                                        item.selection.building?.name,
                                        item.selection.floor?.name
                                    ).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { viewModel.deleteHistory(item) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "删除 ${item.label}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ElectricityAlertSettings(
    onBack: () -> Unit,
    viewModel: ElectricityDepositViewModel
) {
    val configuration by viewModel.alertConfiguration.collectAsState()
    val selectedController by viewModel.selectedController.collectAsState()
    val selectedCampus by viewModel.selectedCampus.collectAsState()
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentRoom = if ((!selectedController.requiresCampus || selectedCampus != null) &&
        selectedBuilding != null && selectedFloor != null && selectedRoom != null
    ) ElectricityAlertRoom(
        RoomSelectionInfo(
            selectedCampus, selectedBuilding, selectedFloor, selectedRoom, selectedController
        )
    ) else null
    val dialogModifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
    var editingRoom by remember { mutableStateOf<ElectricityAlertRoom?>(null) }
    var threshold by rememberSaveable { mutableStateOf("") }
    val roomToEdit = editingRoom
    if (roomToEdit != null) {
        val thresholdDays = threshold.toIntOrNull()?.takeIf { it in 1..30 }
        AppDialog(
            title = "提醒阈值",
            onDismiss = { editingRoom = null },
            modifier = dialogModifier,
            contentScrollable = true,
            actions = listOf(
                AppDialogAction("取消", { editingRoom = null }),
                AppDialogAction(
                    label = "移除",
                    style = AppDialogActionStyle.Danger,
                    onClick = {
                        viewModel.removeAlertRoom(roomToEdit)
                        editingRoom = null
                    }
                ),
                AppDialogAction(
                    label = "保存",
                    onClick = {
                        thresholdDays?.let { viewModel.updateAlertThreshold(roomToEdit, it) }
                        editingRoom = null
                    },
                    style = AppDialogActionStyle.Primary,
                    enabled = thresholdDays != null
                )
            )
        ) {
            Text(roomToEdit.label, style = MaterialTheme.typography.bodyMedium)
            AppTextField(
                value = threshold,
                onValueChange = { input ->
                    if (input.length <= 2 && input.all(Char::isDigit)) threshold = input
                },
                label = "剩余天数（1–30 天）",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text(
                "预计余额不足以继续使用这些天数时提醒。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    val alreadyAdded = currentRoom != null && configuration.rooms.any { it.key == currentRoom.key }
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "电费预警",
            onBack = onBack,
            backdrop = backdrop
        ) {
            SettingsSection(
                title = null,
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsToggleRow(
                    title = "余额不足提醒",
                    selected = configuration.enabled,
                    onSelectedChange = viewModel::setAlertEnabled,
                    backdrop = backdrop,
                    showDivider = false
                )
            }

            if (errorMessage != null) {
                SettingsSection(
                    title = null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    backdrop = backdrop
                ) {
                    SettingsActionRow(
                        title = "电控信息加载失败",
                        subtitle = errorMessage,
                        destructive = true,
                        showChevron = false,
                        showDivider = false,
                        onClick = viewModel::retry
                    )
                }
            }

            SettingsSection(
                title = "选择房间",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                ElectricityRoomSelectorsContent(viewModel = viewModel)
            }

            SettingsSection(
                title = "当前房间",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsActionRow(
                    title = currentRoom?.label ?: "尚未选择",
                    showChevron = false,
                    onClick = {}
                )
                SettingsActionRow(
                    title = if (alreadyAdded) "当前房间已添加" else "添加当前房间",
                    showChevron = false,
                    showDivider = false,
                    onClick = {
                        if (currentRoom != null && !isLoading && !alreadyAdded) {
                            viewModel.addCurrentAlertRoom()
                        }
                    }
                )
            }

            if (configuration.rooms.isNotEmpty()) {
                SettingsSection(
                    title = "已添加房间",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    backdrop = backdrop
                ) {
                    configuration.rooms.forEachIndexed { index, room ->
                        SettingsActionRow(
                            title = room.label,
                            value = "不足 ${room.thresholdDays} 天时提醒",
                            showDivider = index < configuration.rooms.lastIndex,
                            onClick = {
                                threshold = room.thresholdDays.toString()
                                editingRoom = room
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ElectricityRoomSelectorsContent(viewModel: ElectricityDepositViewModel) {
    val selectedController by viewModel.selectedController.collectAsState()
    val campusList by viewModel.campusList.collectAsState()
    val selectedCampus by viewModel.selectedCampus.collectAsState()
    val buildingsList by viewModel.buildingsList.collectAsState()
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()
    val floorsList by viewModel.floorsList.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val roomsList by viewModel.roomsList.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val controllerOptions = remember {
        ElectricityController.entries.map { AppSelectOption(it, it.displayName) }
    }
    val campusOptions = remember(campusList) {
        campusList.map { AppSelectOption(it, it.name) }
    }
    val buildingOptions = remember(buildingsList) {
        buildingsList.map { AppSelectOption(it, it.name) }
    }
    val floorOptions = remember(floorsList) {
        floorsList.map { AppSelectOption(it, it.name) }
    }
    val roomOptions = remember(roomsList) {
        roomsList.map { AppSelectOption(it, it.name) }
    }

    val loadingSelector = when {
        !isLoading -> null
        selectedController.requiresCampus && selectedCampus == null -> ElectricitySelectorLevel.Campus
        selectedBuilding == null -> ElectricitySelectorLevel.Building
        selectedFloor == null -> ElectricitySelectorLevel.Floor
        selectedRoom == null -> ElectricitySelectorLevel.Room
        else -> ElectricitySelectorLevel.Room
    }
    val selectorContent: @Composable ColumnScope.() -> Unit = {
        AppSelectField(
            label = "电控入口",
            selected = selectedController,
            options = controllerOptions,
            onSelected = viewModel::onControllerSelected,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            miuixStandalone = false,
            grouped = true
        )
        if (selectedController.requiresCampus) {
            ElectricitySelectorField(
                label = "校区",
                selected = selectedCampus,
                options = campusOptions,
                onSelected = viewModel::onCampusSelected,
                modifier = Modifier,
                placeholder = "请选择校区",
                enabled = !isLoading,
                loading = loadingSelector == ElectricitySelectorLevel.Campus
            )
        }
        ElectricitySelectorField(
            label = "楼栋",
            selected = selectedBuilding,
            options = buildingOptions,
            onSelected = viewModel::onBuildingSelected,
            modifier = Modifier,
            placeholder = if (selectedController.requiresCampus && selectedCampus == null) "请先选择校区" else "请选择楼栋",
            enabled = (!selectedController.requiresCampus || selectedCampus != null) && !isLoading,
            loading = loadingSelector == ElectricitySelectorLevel.Building
        )
        ElectricitySelectorField(
            label = "楼层",
            selected = selectedFloor,
            options = floorOptions,
            onSelected = viewModel::onfloorSelected,
            modifier = Modifier,
            placeholder = if (selectedBuilding == null) "请先选择楼栋" else "请选择楼层",
            enabled = selectedBuilding != null && !isLoading,
            loading = loadingSelector == ElectricitySelectorLevel.Floor
        )
        ElectricitySelectorField(
            label = "房间",
            selected = selectedRoom,
            options = roomOptions,
            onSelected = viewModel::onRoomSelected,
            modifier = Modifier,
            placeholder = if (selectedFloor == null) "请先选择楼层" else "请选择房间",
            enabled = selectedFloor != null && !isLoading,
            loading = loadingSelector == ElectricitySelectorLevel.Room
        )
    }
    Column(Modifier.fillMaxWidth()) {
        selectorContent()
    }
}

/** 缴费页原样：选择器包在原有卡片里，设置页使用无卡片的 [ElectricityRoomSelectorsContent]。 */
@Composable
private fun ElectricityRoomSelectors(viewModel: ElectricityDepositViewModel) {
    AppSectionCard(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = { ElectricityRoomSelectorsContent(viewModel) }
    )
}

@Composable
private fun ElectricitySelectorField(
    label: String,
    selected: CampusDataItem?,
    options: List<AppSelectOption<CampusDataItem>>,
    onSelected: (CampusDataItem) -> Unit,
    modifier: Modifier,
    placeholder: String,
    enabled: Boolean,
    loading: Boolean
) {
    Box(modifier = modifier.fillMaxWidth()) {
        AppSelectField(
            label = label,
            selected = selected,
            options = options,
            onSelected = onSelected,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = enabled,
            miuixStandalone = false,
            grouped = true
        )
        if (loading) {
            AppCircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp),
                size = 20.dp,
                strokeWidth = 2.5.dp
            )
        }
    }
}

private enum class ElectricitySelectorLevel {
    Campus,
    Building,
    Floor,
    Room
}

private const val PAYMENT_RESULT_DISPLAY_DURATION_MS = 3_000L
