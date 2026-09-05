package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.ui.component.SecurePaymentPasswordDialog
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppComponentTokens
import com.ahu.ahutong.ui.components.AppScrollablePageLayout
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.GlassCard
import com.ahu.ahutong.ui.components.SecondaryPageScaffold
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.state.CampusDataItem
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.delay

@Composable
fun ElectricityDeposit(
    onBack: () -> Unit,
    onOpenRecentRooms: () -> Unit,
    viewModel: ElectricityDepositViewModel = hiltViewModel()
) {
    val behaviorReporter = rememberBehaviorActionReporter()
    val payState by viewModel.payState.collectAsState()
    val selectedController by viewModel.selectedController.collectAsState()
    val campusList by viewModel.campusList.collectAsState()
    val selectedCampus by viewModel.selectedCampus.collectAsState()
    val buildingsList by viewModel.buildingsList.collectAsState()
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()
    val floorsList by viewModel.floorsList.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val roomsList by viewModel.roomsList.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()
    val roomInfo by viewModel.roomInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val historyOptions by viewModel.historyOptions.collectAsState()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var amount by rememberSaveable { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(payState) {
        if (payState is PayState.Succeeded || payState is PayState.Failed) {
            delay(PAYMENT_RESULT_DISPLAY_DURATION_MS)
            viewModel.resetPaymentState()
        }
    }

    val canPay = (!selectedController.requiresCampus || selectedCampus != null) &&
        selectedBuilding != null && selectedFloor != null && selectedRoom != null &&
        amount.toDoubleOrNull()?.let { it > 0.0 } == true &&
        !isLoading && payState is PayState.Idle
    val horizontalPadding = if (isRadiantUi) 0.dp else 16.dp

    val pageContent: @Composable ColumnScope.() -> Unit = {
        if (errorMessage != null) {
            val errorContent: @Composable ColumnScope.() -> Unit = {
                Text("电控信息加载失败", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                AppButton(
                    onClick = viewModel::retry,
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.Secondary
                ) {
                    Text("重新加载")
                }
            }
            if (isRadiantUi) {
                GlassCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    overlayColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = errorContent
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .fillMaxWidth()
                        .appLiquidGlassSurface(
                            shape = AppComponentTokens.CardShape,
                            fallbackColor = MaterialTheme.colorScheme.errorContainer,
                            level = LiquidGlassSurfaceLevel.Panel
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = errorContent
                )
            }
        }

        if (historyOptions.isNotEmpty()) {
            AppButton(
                onClick = onOpenRecentRooms,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(),
                enabled = !isLoading,
                variant = AppButtonVariant.Secondary
            ) {
                Text("最近使用的房间")
            }
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
                miuixStandalone = true
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
                placeholder = if (selectedController.requiresCampus) "请先选择校区" else "请选择楼栋",
                enabled = (!selectedController.requiresCampus || selectedCampus != null) && !isLoading,
                loading = loadingSelector == ElectricitySelectorLevel.Building
            )
            ElectricitySelectorField(
                label = "楼层",
                selected = selectedFloor,
                options = floorOptions,
                onSelected = viewModel::onfloorSelected,
                modifier = Modifier,
                placeholder = "请先选择楼栋",
                enabled = selectedBuilding != null && !isLoading,
                loading = loadingSelector == ElectricitySelectorLevel.Floor
            )
            ElectricitySelectorField(
                label = "房间",
                selected = selectedRoom,
                options = roomOptions,
                onSelected = viewModel::onRoomSelected,
                modifier = Modifier,
                placeholder = "请先选择楼层",
                enabled = selectedFloor != null && !isLoading,
                loading = loadingSelector == ElectricitySelectorLevel.Room
            )
        }
        if (isRadiantUi) {
            GlassCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = selectorContent
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = selectorContent
            )
        }

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
            if (isRadiantUi) {
                GlassCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = roomContent
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .fillMaxWidth()
                        .appLiquidGlassSurface(
                            shape = AppComponentTokens.CardShape,
                            fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                            level = LiquidGlassSurfaceLevel.Panel
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = roomContent
                )
            }
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
        if (isRadiantUi) {
            GlassCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = amountContent
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = amountContent
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = payState) {
                PayState.Idle -> Unit
                PayState.InProgress -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    Text("  正在提交缴费", style = MaterialTheme.typography.bodyLarge)
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

    if (isRadiantUi) {
        SecondaryPageScaffold(
            title = "电控缴费",
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = pageContent
            )
        }
    } else {
        AppScrollablePageLayout(
            title = "电控缴费",
            onBack = onBack,
            modifier = Modifier
                .fillMaxSize()
                .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
            bottomPadding = 48.dp,
            content = pageContent
        )
    }

    if (showPasswordDialog) {
        SecurePaymentPasswordDialog(
            password = password,
            onPasswordChange = {
                password = it
                passwordError = null
            },
            title = "请输入校园卡密码",
            errorMessage = passwordError,
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
                    behaviorReporter.organic(AppActionId.CONFIRM_ELECTRICITY_PAYMENT)
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
    AppScrollablePageLayout(
        title = "最近使用的房间",
        onBack = onBack,
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        bottomPadding = 48.dp
    ) {
        if (historyOptions.isEmpty()) {
            Text(
                text = "暂无最近使用的房间",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            historyOptions.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppButton(
                        onClick = {
                            viewModel.selectHistory(item)
                            onRoomSelected()
                        },
                        modifier = Modifier.weight(1f),
                        variant = AppButtonVariant.Secondary
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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
                    }
                    AppButton(
                        onClick = { viewModel.deleteHistory(item) },
                        variant = AppButtonVariant.Destructive
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
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
            miuixStandalone = true
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
