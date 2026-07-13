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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.feature.payment.R
import com.ahu.ahutong.ui.components.AhuChip
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuInsetCard
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ElectricityDeposit(
    viewModel: ElectricityDepositViewModel = hiltViewModel()
) {
    val payState = viewModel.payState.collectAsState()
    LaunchedEffect(payState.value) {
        when (payState.value) {
            is PayState.Succeeded, is PayState.Failed -> {
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
            level >= 1 && selectedCampus == null -> context.getString(R.string.please_select_campus_first)
            level >= 2 && selectedBuilding == null -> context.getString(R.string.please_select_building_first)
            level >= 3 && selectedFloor == null -> context.getString(R.string.please_select_floor_first)
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

    AhuScreen(clearBottomNav = false) {
        AhuPageHeader(title = stringResource(R.string.electricity_deposit))

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { campusDropdownExpanded = true },
            ) {
                Text(
                    text = stringResource(R.string.select_campus),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { campusDropdownExpanded = true }
                ) {
                    Text(
                        text = selectedCampus?.name ?: stringResource(R.string.please_select_campus)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.expand_campus_list)
                    )

                    DropdownMenu(
                        expanded = campusDropdownExpanded,
                        modifier = Modifier
                            .heightIn(max = 350.dp)
                            .background(AhuColors.pageBackground),
                        onDismissRequest = { campusDropdownExpanded = false },
                    ) {
                        campusList.forEach { campus ->
                            DropdownMenuItem(
                                text = { Text(campus.name, color = AhuColors.onSurface) },
                                onClick = {
                                    viewModel.onCampusSelected(campus)
                                    campusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { openBuildingMenu() },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.select_building), style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { openBuildingMenu() }
                ) {
                    Text(
                        text = selectedBuilding?.name ?: stringResource(R.string.please_select_building)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.expand_building_list)
                    )

                    DropdownMenu(
                        expanded = buildingsDropdownExpanded,
                        modifier = Modifier
                            .heightIn(max = 450.dp)
                            .background(AhuColors.pageBackground),
                        onDismissRequest = { buildingsDropdownExpanded = false },
                    ) {
                        buildingsList.forEach { building ->
                            DropdownMenuItem(
                                text = { Text(building.name, color = AhuColors.onSurface) },
                                onClick = {
                                    viewModel.onBuildingSelected(building)
                                    buildingsDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { openFloorMenu() },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.select_floor), style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { openFloorMenu() },
                ) {
                    Text(
                        text = selectedFloor?.name ?: stringResource(R.string.please_select_floor)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.expand_floor_list)
                    )

                    DropdownMenu(
                        expanded = floorsDropdownExpanded,
                        modifier = Modifier
                            .heightIn(max = 450.dp)
                            .background(AhuColors.pageBackground),
                        onDismissRequest = { floorsDropdownExpanded = false },
                    ) {
                        floorsList.forEach { floor ->
                            DropdownMenuItem(
                                text = { Text(floor.name, color = AhuColors.onSurface) },
                                onClick = {
                                    viewModel.onfloorSelected(floor)
                                    floorsDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { openRoomMenu() },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.select_room), style = MaterialTheme.typography.titleMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { openRoomMenu() },
                ) {
                    Text(
                        text = selectedRoom?.name ?: stringResource(R.string.please_select_room)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.expand_room_list)
                    )

                    DropdownMenu(
                        expanded = roomsDropdownExpanded,
                        onDismissRequest = { roomsDropdownExpanded = false },
                        modifier = Modifier
                            .heightIn(max = 500.dp)
                            .background(AhuColors.pageBackground)
                    ) {
                        roomsList.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(room.name, color = AhuColors.onSurface) },
                                onClick = {
                                    viewModel.onRoomSelected(room)
                                    roomsDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

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
                            AhuChip(
                                text = item.label,
                                selected = true,
                                onClick = { viewModel.selectHistory(item) },
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
                        onClick = {
                            infoClickCount++
                            currentToast?.cancel()
                            val message = when {
                                infoClickCount == 1 -> context.getString(R.string.charge_info_hint_1)
                                infoClickCount == 2 -> context.getString(R.string.charge_info_hint_2)
                                infoClickCount == 3 -> context.getString(R.string.charge_info_hint_3)
                                infoClickCount == 4 -> context.getString(R.string.charge_info_hint_4)
                                infoClickCount >= 5 -> {
                                    val chargeInfo = viewModel.getElectricityChargeInfo()
                                    if (chargeInfo != null) {
                                        context.getString(
                                            R.string.charge_info_total,
                                            chargeInfo.firstChargeDate,
                                            "%.2f".format(chargeInfo.totalAmount)
                                        )
                                    } else {
                                        context.getString(R.string.no_charge_record)
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
                        onLongClick = {
                            showResetDialog = true
                        }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.info), style = MaterialTheme.typography.titleMedium)
                Text(text = roomInfo?.replace("，", "\n") ?: "")
            }
        }

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.payment_amount),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            TextField(
                value = amount,
                onValueChange = { newText ->
                    if (newText.isEmpty()) {
                        amount = newText
                        return@TextField
                    }
                    val regex = Regex("^\\d*\\.?\\d{0,2}$")
                    if (regex.matches(newText)) {
                        amount = newText
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = {
                    Text(
                        stringResource(R.string.please_enter_amount),
                        color = AhuColors.onSurface.copy(alpha = 0.45f)
                    )
                },
                textStyle = TextStyle(fontSize = 16.sp, color = AhuColors.onSurface),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AhuDimens.ContentHorizontal),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(ContinuousCapsule)
                    .background(
                        animateColorAsState(
                            targetValue = when (payState.value) {
                                is PayState.Idle -> AhuColors.primaryAction
                                is PayState.InProgress -> 70.a1 withNight 60.a1
                                is PayState.Failed -> Color.Red
                                is PayState.Succeeded -> 70.a1 withNight 60.a1
                            },
                            label = "payStateBg"
                        ).value
                    )
                    .animateContentSize(spring(stiffness = Spring.StiffnessLow))
            ) {
                when (payState.value) {
                    is PayState.Idle -> {
                        Text(
                            text = stringResource(R.string.confirm),
                            modifier = Modifier
                                .clickable(
                                    role = Role.Button,
                                    onClick = {
                                        when {
                                            selectedCampus == null -> showToast(context.getString(R.string.please_select_campus_first))
                                            selectedBuilding == null -> showToast(context.getString(R.string.please_select_building_first))
                                            selectedFloor == null -> showToast(context.getString(R.string.please_select_floor_first))
                                            selectedRoom == null -> showToast(context.getString(R.string.please_select_room_first))
                                            amount.isBlank() -> showToast(context.getString(R.string.please_enter_payment_amount))
                                            (amount.toDoubleOrNull() ?: 0.0) <= 0.0 -> showToast(context.getString(R.string.please_enter_valid_amount))
                                            else -> showDialog = true
                                        }
                                    }
                                )
                                .padding(24.dp, 16.dp),
                            color = AhuColors.onPrimaryAction,
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
                                text = stringResource(R.string.paying_ellipsis),
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
                                text = stringResource(
                                    R.string.payment_success_order,
                                    (payState.value as PayState.Succeeded).message
                                ),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { },
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
                                text = stringResource(R.string.payment_failed),
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

        if (showDialog) {
            AhuDialog(onDismissRequest = { showDialog = false }) {
                Text(
                    text = stringResource(R.string.enter_campus_card_password),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = AhuColors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                password = input
                                errorMsg = null
                            }
                        },
                        label = {
                            Text(
                                stringResource(R.string.password_6_digits),
                                color = AhuColors.onSurface.copy(alpha = 0.55f)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = errorMsg != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AhuColors.onSurface,
                            unfocusedTextColor = AhuColors.onSurface,
                            focusedBorderColor = AhuColors.onSurface.copy(alpha = 0.7f)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    AhuPrimaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = {
                            showDialog = false
                            password = ""
                            errorMsg = null
                        },
                        containerColor = AhuColors.cardStrong,
                        contentColor = AhuColors.onSurface,
                    )
                    AhuPrimaryButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            if (password.length == 6) {
                                showDialog = false
                                viewModel.pay(amount, password)
                            } else {
                                errorMsg = context.getString(R.string.password_must_be_6_digits)
                            }
                        },
                    )
                }
            }
        }

        if (showResetDialog) {
            AhuDialog(onDismissRequest = { showResetDialog = false }) {
                Text(
                    text = stringResource(R.string.confirm_operation),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = AhuColors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.confirm_clear_charge_record),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = AhuColors.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    AhuPrimaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showResetDialog = false },
                        containerColor = AhuColors.cardStrong,
                        contentColor = AhuColors.onSurface,
                    )
                    AhuPrimaryButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            viewModel.clearElectricityChargeInfo()
                            Toast.makeText(
                                context,
                                context.getString(R.string.charge_record_cleared),
                                Toast.LENGTH_SHORT
                            ).show()
                            showResetDialog = false
                        },
                    )
                }
            }
        }
    }
}
