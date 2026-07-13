package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.style.TextAlign
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
import com.ahu.ahutong.ui.state.BathroomDepositViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BathroomDeposit(
    viewmodel: BathroomDepositViewModel = hiltViewModel()
) {
    val payState = viewmodel.payState.collectAsState()
    LaunchedEffect(payState.value) {
        when (payState.value) {
            is PayState.Succeeded, is PayState.Failed -> {
                delay(1000)
                viewmodel.resetPaymentState()
            }

            else -> {
            }
        }
    }
    val bathroomZhuyuan = stringResource(R.string.bathroom_zhuyuan_longhe)
    val bathroomJuyuan = stringResource(R.string.bathroom_juyuan_huiyuan)
    val options = listOf(bathroomZhuyuan, bathroomJuyuan)
    var expanded by remember { mutableStateOf(false) }
    var bathroom by remember { mutableStateOf(options[0]) }

    var amount by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }

    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val info = viewmodel.info.collectAsState()

    var lastTel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        lastTel = viewmodel.getSavedPhone()
    }

    val textFieldColors = TextFieldDefaults.colors(
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    var showDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AhuScreen(
        clearBottomNav = false,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            focusManager.clearFocus()
        },
    ) {
        AhuPageHeader(title = stringResource(R.string.bathroom_deposit))

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.select_bathroom),
                    style = MaterialTheme.typography.titleMedium
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = bathroom,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .width(150.dp),
                        colors = textFieldColors,
                        textStyle = TextStyle(
                            textAlign = TextAlign.End,
                            fontSize = 16.sp,
                            color = AhuColors.onSurface
                        ),
                        singleLine = true,
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(AhuColors.pageBackground),
                    ) {
                        options.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(selectionOption, color = AhuColors.onSurface)
                                },
                                onClick = {
                                    bathroom = selectionOption
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.phone_number), style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = tel,
                    onValueChange = { value ->
                        tel = value
                    },
                    modifier = Modifier
                        .width(150.dp)
                        .onFocusChanged {
                            if (!it.isFocused && hasFocus && !tel.isEmpty()) {
                                viewmodel.getBathroomInfo(bathroom, tel)
                            }
                            hasFocus = it.isFocused
                        },
                    colors = textFieldColors,
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        color = AhuColors.onSurface
                    ),
                    singleLine = true,
                )
            }

            lastTel?.let {
                Row(horizontalArrangement = Arrangement.End) {
                    AnimatedVisibility(
                        visible = (lastTel != null && !hasFocus),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AhuChip(
                                text = stringResource(R.string.last_recharge, it),
                                selected = true,
                                onClick = {
                                    tel = it
                                    viewmodel.getBathroomInfo(bathroom, tel)
                                    lastTel = null
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.info), style = MaterialTheme.typography.titleMedium)

                val unknownError = stringResource(R.string.unknown_error)
                val displayText = info.value?.let { it ->
                    when {
                        it.data.map == null -> it.data.message ?: unknownError
                        it.data.map!!.showData != null -> {
                            val showData = it.data.map!!.showData!!
                            context.getString(
                                R.string.bathroom_info_detail,
                                showData.phone,
                                showData.cashAmount,
                                showData.giftAmount
                            )
                        }

                        it.data.map!!.data?.message != null -> it.data.map!!.data!!.message!!
                        else -> unknownError
                    }
                } ?: ""

                Text(text = displayText)
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
                colors = textFieldColors,
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
                when (val state = payState.value) {
                    PayState.Idle -> {
                        CompositionLocalProvider(LocalIndication provides ripple(color = AhuColors.onPrimaryAction)) {
                            Text(
                                text = stringResource(R.string.confirm),
                                modifier = Modifier
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            if (!amount.isEmpty() && info.value != null) {
                                                showDialog = true
                                            }
                                        }
                                    )
                                    .padding(24.dp, 16.dp),
                                color = AhuColors.onPrimaryAction,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    PayState.InProgress -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                color = 100.n1,
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = stringResource(R.string.paying),
                                modifier = Modifier.padding(4.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = 100.n1
                            )
                            Text(
                                text = stringResource(R.string.payment_failed_with_message, state.message),
                                modifier = Modifier.padding(4.dp),
                                color = 100.n1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                    is PayState.Succeeded -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = 100.n1
                            )
                            Text(
                                text = stringResource(R.string.payment_success_order, state.message),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { },
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
                        Text(text = errorMsg!!)
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
                                viewmodel.pay(
                                    bathroom = bathroom,
                                    amount = amount,
                                    password = password
                                )
                            } else {
                                errorMsg = context.getString(R.string.password_must_be_6_digits)
                            }
                        },
                    )
                }
            }
        }
    }
}
