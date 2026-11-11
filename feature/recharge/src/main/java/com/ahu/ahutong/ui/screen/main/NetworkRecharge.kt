package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.component.SecurePaymentPasswordDialog
import com.ahu.ahutong.ui.state.NetworkRechargePageState
import com.ahu.ahutong.ui.state.NetworkRechargeUiData
import com.ahu.ahutong.ui.state.NetworkRechargeViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.personalization.action.AppActionId
import kotlinx.coroutines.delay

@Composable
fun NetworkRecharge(
    onBack: () -> Unit,
    viewModel: NetworkRechargeViewModel = hiltViewModel()
) {
    val pageState by viewModel.pageState.collectAsState()
    val payState by viewModel.payState.collectAsState()
    val builtInKeyboard by viewModel.builtInKeyboard.collectAsState()
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
            is PayState.Succeeded -> {
                delay(1_000L)
                viewModel.load()
            }
            is PayState.Failed -> {
                delay(PAYMENT_RESULT_DISPLAY_DURATION_MS)
                viewModel.resetPayState()
            }

            else -> Unit
        }
    }

    val pageContent: @Composable ColumnScope.() -> Unit = {
        when (val state = pageState) {
            NetworkRechargePageState.Loading -> {
                AppStateCard.Inline(loading = true, horizontalInset = 0.dp)
            }

            is NetworkRechargePageState.Error -> {
                AppStateCard.Inline(
                    horizontalInset = 0.dp,
                    message = state.message,
                    actionLabel = "重试",
                    onAction = { viewModel.load() }
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

    AppPageScaffold(
        title = "网费充值",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        bottomPadding = 48.dp,
        content = pageContent
    )

    if (showDialog) {
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
                showDialog = false
                password = ""
                passwordError = null
            },
            onConfirm = { confirmedPassword ->
                if (confirmedPassword.length == 6) {
                    showDialog = false
                    viewModel.onRechargeSubmitted()
                    viewModel.pay(amount, confirmedPassword)
                    password = ""
                    passwordError = null
                } else {
                    passwordError = "密码必须是6位数字"
                }
            }
        )
    }
}

private const val PAYMENT_RESULT_DISPLAY_DURATION_MS = 3_000L

@Composable
private fun NetworkAccountCard(
    data: NetworkRechargeUiData
) {
    AppSectionCard(
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top
    ) {
        NetworkAccountContent(data = data)
    }
}

@Composable
private fun NetworkAccountContent(data: NetworkRechargeUiData) {
    Column(
        modifier = Modifier.padding(16.dp),
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
    AppSectionCard(
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top
    ) {
        AmountCardContent(
            amount = amount,
            amountError = amountError,
            quickAmounts = quickAmounts,
            maxAmount = maxAmount,
            onAmountChange = onAmountChange,
            onQuickAmountClick = onQuickAmountClick,
            onDone = onDone
        )
    }
}

@Composable
private fun AmountCardContent(
    amount: String,
    amountError: String?,
    quickAmounts: List<String>,
    maxAmount: String?,
    onAmountChange: (String) -> Unit,
    onQuickAmountClick: (String) -> Unit,
    onDone: () -> Unit
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
                    AppFilterChip(
                        selected = amount == normalizeQuickAmount(quickAmount),
                        onClick = { onQuickAmountClick(quickAmount) },
                        label = { Text(quickAmount) }
                    )
                }
            }
        }

        AppTextField(
            value = amount,
            onValueChange = onAmountChange,
            label = if (maxAmount.isNullOrBlank()) "金额（元）" else "金额（最高 $maxAmount 元）",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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

@Composable
private fun RechargeActionRow(
    payState: PayState,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (payState) {
            PayState.Idle -> Unit
            PayState.InProgress -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCircularProgressIndicator(size = 22.dp, strokeWidth = 3.dp)
                Text("正在充值", style = MaterialTheme.typography.bodyLarge)
            }
            is PayState.Failed -> StatusMessage(
                icon = Icons.Default.Close,
                message = "充值失败：${payState.message}",
                isError = true
            )
            is PayState.Succeeded -> StatusMessage(
                icon = Icons.Default.Check,
                message = "充值成功，正在刷新账户信息",
                isError = false
            )
        }
        AppButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = payState is PayState.Idle
        ) {
            Text(if (payState is PayState.InProgress) "正在充值" else "确认充值")
        }
    }
}

@Composable
private fun StatusMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    isError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        Text(message, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun normalizeQuickAmount(raw: String): String {
    return Regex("\\d+(?:\\.\\d{1,2})?")
        .find(raw)
        ?.value
        ?: raw
}
