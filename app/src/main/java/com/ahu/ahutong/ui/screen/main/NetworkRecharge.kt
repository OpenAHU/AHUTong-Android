package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.ui.components.GlassCard
import com.ahu.ahutong.ui.components.SecondaryPageScaffold
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.NetworkRechargePageState
import com.ahu.ahutong.ui.state.NetworkRechargeUiData
import com.ahu.ahutong.ui.state.NetworkRechargeViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import kotlinx.coroutines.delay

@Composable
fun NetworkRecharge(
    viewModel: NetworkRechargeViewModel = viewModel()
) {
    val behaviorReporter = rememberBehaviorActionReporter()
    val pageState by viewModel.pageState.collectAsState()
    val payState by viewModel.payState.collectAsState()
    val focusManager = LocalFocusManager.current

    var amount by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(payState) {
        when (payState) {
            is PayState.Succeeded, is PayState.Failed -> {
                delay(1200)
                viewModel.resetPayState()
            }

            else -> Unit
        }
    }

    if (isRadiantUi) {
        SecondaryPageScaffold(
            title = "网费充值",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    when (val state = pageState) {
                        NetworkRechargePageState.Loading -> LoadingCard()
                        is NetworkRechargePageState.Error -> ErrorCard(
                            message = state.message,
                            onRetry = { viewModel.load() }
                        )
                        is NetworkRechargePageState.Ready -> {
                            NetworkAccountCard(data = state.data)
                            AmountCard(
                                amount = amount,
                                amountError = amountError,
                                quickAmounts = state.data.quickAmounts,
                                maxAmount = state.data.maxAmount,
                                onAmountChange = { value ->
                                    if (value.isEmpty()) {
                                        amount = value
                                        amountError = null
                                        return@AmountCard
                                    }
                                    val regex = Regex("^\\d*\\.?\\d{0,2}$")
                                    if (regex.matches(value)) {
                                        amount = value
                                        amountError = null
                                    }
                                },
                                onQuickAmountClick = { quickAmount ->
                                    amount = normalizeQuickAmount(quickAmount)
                                    amountError = null
                                },
                                onDone = { focusManager.clearFocus() }
                            )

                            RechargeActionRow(
                                payState = payState,
                                onConfirm = {
                                    focusManager.clearFocus()
                                    val amountValue = amount.toDoubleOrNull()
                                    val maxAmount = state.data.maxAmount?.toDoubleOrNull()
                                    amountError = when {
                                        amount.isBlank() -> "请输入充值金额"
                                        amountValue == null || amountValue <= 0.0 -> "请输入有效金额"
                                        maxAmount != null && amountValue > maxAmount ->
                                            "单次最高可充值 ${state.data.maxAmount} 元"

                                        else -> null
                                    }
                                    if (amountError == null) {
                                        password = ""
                                        passwordError = null
                                        showDialog = true
                                    }
                                }
                            )
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
                text = "网费充值",
                modifier = Modifier.padding(24.dp, 32.dp),
                style = MaterialTheme.typography.headlineMedium
            )

        when (val state = pageState) {
            NetworkRechargePageState.Loading -> {
                LoadingCard()
            }

            is NetworkRechargePageState.Error -> {
                ErrorCard(
                    message = state.message,
                    onRetry = { viewModel.load() }
                )
            }

            is NetworkRechargePageState.Ready -> {
                NetworkAccountCard(data = state.data)
                AmountCard(
                    amount = amount,
                    amountError = amountError,
                    quickAmounts = state.data.quickAmounts,
                    maxAmount = state.data.maxAmount,
                    onAmountChange = { value ->
                        if (value.isEmpty()) {
                            amount = value
                            amountError = null
                            return@AmountCard
                        }

                        val regex = Regex("^\\d*\\.?\\d{0,2}$")
                        if (regex.matches(value)) {
                            amount = value
                            amountError = null
                        }
                    },
                    onQuickAmountClick = { quickAmount ->
                        amount = normalizeQuickAmount(quickAmount)
                        amountError = null
                    },
                    onDone = { focusManager.clearFocus() }
                )

                RechargeActionRow(
                    payState = payState,
                    onConfirm = {
                        focusManager.clearFocus()
                        val amountValue = amount.toDoubleOrNull()
                        val maxAmount = state.data.maxAmount?.toDoubleOrNull()
                        amountError = when {
                            amount.isBlank() -> "请输入充值金额"
                            amountValue == null || amountValue <= 0.0 -> "请输入有效金额"
                            maxAmount != null && amountValue > maxAmount -> "单次最高可充值 ${state.data.maxAmount} 元"
                            else -> null
                        }
                        if (amountError == null) {
                            password = ""
                            passwordError = null
                            showDialog = true
                        }
                    }
                )
            }
        }
    }
    }

    if (showDialog) {
        AlertDialog(
            containerColor = 100.n1 withNight 20.n1,
            titleContentColor = 10.n1 withNight 90.n1,
            textContentColor = 10.n1 withNight 90.n1,
            onDismissRequest = { showDialog = false },
            title = { Text("请输入校园卡密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                password = input
                                passwordError = null
                            }
                        },
                        label = { Text("密码 (6位数字)", color = 40.n1 withNight 60.n1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = 10.n1 withNight 90.n1,
                            unfocusedTextColor = 10.n1 withNight 90.n1,
                            focusedBorderColor = 20.n1 withNight 80.n1
                        )
                    )
                    passwordError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (password.length == 6) {
                            showDialog = false
                            behaviorReporter.organic(AppActionId.SUBMIT_NETWORK_RECHARGE)
                            viewModel.pay(amount, password)
                            password = ""
                            passwordError = null
                        } else {
                            passwordError = "密码必须是6位数字"
                        }
                    }
                ) {
                    Text("确认", color = 10.n1 withNight 90.n1)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        password = ""
                        passwordError = null
                    }
                ) {
                    Text("取消", color = 10.n1 withNight 90.n1)
                }
            }
        )
    }
}

@Composable
private fun LoadingCard() {
    if (isRadiantUi) {
        GlassCard(
            containerColor = 100.n1 withNight 20.n1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = 30.n1 withNight 70.n1)
            }
        }
    } else {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(24.dp))
                .background(100.n1 withNight 20.n1)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = 30.n1 withNight 70.n1)
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    if (isRadiantUi) {
        GlassCard(
            containerColor = 100.n1 withNight 20.n1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    color = 10.n1 withNight 90.n1,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "重试",
                    modifier = Modifier.clickable(onClick = onRetry),
                    color = 30.n1 withNight 70.n1,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(24.dp))
                .background(100.n1 withNight 20.n1)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                color = 10.n1 withNight 90.n1,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "重试",
                modifier = Modifier.clickable(onClick = onRetry),
                color = 30.n1 withNight 70.n1,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun NetworkAccountCard(
    data: NetworkRechargeUiData
) {
    if (isRadiantUi) {
        GlassCard(
            containerColor = 100.n1 withNight 20.n1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = data.feeName,
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "充值账号",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = data.account.ifBlank { "--" },
                        color = 10.n1 withNight 90.n1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                data.stats.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = 40.n1 withNight 60.n1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = value.ifBlank { "--" },
                            color = 10.n1 withNight 90.n1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(24.dp))
                .background(100.n1 withNight 20.n1)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = data.feeName,
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "充值账号",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = data.account.ifBlank { "--" },
                    color = 10.n1 withNight 90.n1,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            data.stats.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = 40.n1 withNight 60.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = value.ifBlank { "--" },
                        color = 10.n1 withNight 90.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountCard(
    amount: String,
    amountError: String?,
    quickAmounts: List<String>,
    maxAmount: String?,
    onAmountChange: (String) -> Unit,
    onQuickAmountClick: (String) -> Unit,
    onDone: () -> Unit
) {
    if (isRadiantUi) {
        GlassCard(
            containerColor = 100.n1 withNight 20.n1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "充值金额",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                if (quickAmounts.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        quickAmounts.forEach { quickAmount ->
                            Text(
                                text = quickAmount,
                                modifier = Modifier
                                    .clip(SmoothRoundedCornerShape(16.dp))
                                    .background(90.a1 withNight 30.n1)
                                    .clickable { onQuickAmountClick(quickAmount) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                color = 10.n1 withNight 90.n1,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                TextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = {
                        Text(
                            text = if (maxAmount.isNullOrBlank()) "请输入金额" else "请输入金额，单次最高 $maxAmount 元",
                            color = 30.n1 withNight 70.n1
                        )
                    },
                    textStyle = TextStyle(fontSize = 16.sp, color = 10.n1 withNight 90.n1),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    singleLine = true
                )

                amountError?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(24.dp))
                .background(100.n1 withNight 20.n1)
        ) {
            Text(
                text = "充值金额",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            if (quickAmounts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    quickAmounts.forEach { quickAmount ->
                        Text(
                            text = quickAmount,
                            modifier = Modifier
                                .clip(SmoothRoundedCornerShape(16.dp))
                                .background(90.a1 withNight 30.n1)
                                .clickable { onQuickAmountClick(quickAmount) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = 10.n1 withNight 90.n1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            TextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                placeholder = {
                    Text(
                        text = if (maxAmount.isNullOrBlank()) "请输入金额" else "请输入金额，单次最高 $maxAmount 元",
                        color = 30.n1 withNight 70.n1
                    )
                },
                textStyle = TextStyle(fontSize = 16.sp, color = 10.n1 withNight 90.n1),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                singleLine = true
            )

            amountError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RechargeActionRow(
    payState: PayState,
    onConfirm: () -> Unit
) {
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
                        targetValue = when (payState) {
                            PayState.Idle -> 90.a1 withNight 85.a1
                            PayState.InProgress -> 70.a1 withNight 60.a1
                            is PayState.Failed -> Color.Red
                            is PayState.Succeeded -> 70.a1 withNight 60.a1
                        }
                    ).value
                )
                .animateContentSize(spring(stiffness = Spring.StiffnessLow))
        ) {
            when (payState) {
                PayState.Idle -> {
                    Text(
                        text = "确认",
                        modifier = Modifier
                            .clickable(onClick = onConfirm)
                            .padding(24.dp, 16.dp),
                        color = 0.n1,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                PayState.InProgress -> {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = 100.n1,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "支付中",
                            color = 100.n1,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                is PayState.Failed -> {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = 100.n1
                        )
                        Text(
                            text = "充值失败：${payState.message}",
                            color = 100.n1,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                is PayState.Succeeded -> {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = 100.n1
                        )
                        Text(
                            text = "充值成功！订单号：${payState.message}",
                            color = 100.n1,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeQuickAmount(raw: String): String {
    return Regex("\\d+(?:\\.\\d{1,2})?")
        .find(raw)
        ?.value
        ?: raw
}
