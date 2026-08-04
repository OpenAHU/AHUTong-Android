package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class CmbRechargeNativeData(
    val studentNumber: String,
    val balance: Double,
    val paymentMethods: List<CmbRechargePaymentMethod>
)

internal data class CmbRechargePaymentMethod(
    val pageIndex: Int,
    val name: String
)

@Composable
internal fun CmbRechargeNativePanel(
    data: CmbRechargeNativeData?,
    errorMessage: String?,
    isSubmitting: Boolean,
    onRetry: () -> Unit,
    onManagePaymentMethods: () -> Unit,
    onSubmit: (amount: String, paymentMethodIndex: Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedPaymentMethodIndex by remember { mutableIntStateOf(-1) }
    var paymentMenuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(data?.paymentMethods) {
        val methods = data?.paymentMethods.orEmpty()
        if (methods.none { it.pageIndex == selectedPaymentMethodIndex }) {
            selectedPaymentMethodIndex = methods.firstOrNull()?.pageIndex ?: -1
        }
    }

    val amountValue = amount.toDoubleOrNull()
    val amountError = when {
        amount.isBlank() -> null
        amountValue == null || amountValue <= 0.0 -> "请输入有效的充值金额"
        amountValue > CMB_RECHARGE_MAX_AMOUNT -> "单次充值金额不能超过 1000 元"
        else -> null
    }
    val selectedMethod = data?.paymentMethods
        ?.firstOrNull { it.pageIndex == selectedPaymentMethodIndex }
    val canSubmit = data != null &&
        selectedMethod != null &&
        amountValue != null &&
        amountValue > 0.0 &&
        amountValue <= CMB_RECHARGE_MAX_AMOUNT &&
        !isSubmitting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (errorMessage != null) {
            NativeRechargeMessageCard(
                title = "充值服务暂不可用",
                message = errorMessage,
                actionText = "重试",
                onAction = onRetry
            )
        }

        NativeRechargeSection(title = "校园卡账户") {
            NativeRechargeInfoRow(
                "学工号",
                data?.studentNumber?.ifBlank { "未获取到" } ?: "—"
            )
            NativeRechargeInfoRow(
                "当前余额",
                data?.let { String.format(Locale.CHINA, "¥ %.2f", it.balance) } ?: "—"
            )
        }

        NativeRechargeSection(title = "充值金额") {
            OutlinedTextField(
                value = amount,
                onValueChange = { value ->
                    if (value.matches(Regex("^\\d{0,4}(\\.\\d{0,2})?$"))) amount = value
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("金额") },
                prefix = { Text("¥ ") },
                placeholder = { Text("请输入充值金额") },
                supportingText = amountError?.let { message -> { Text(message) } },
                isError = amountError != null,
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("50", "100", "200", "500").forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            amount = preset
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 4.dp,
                            vertical = 10.dp
                        )
                    ) {
                        Text("${preset}元")
                    }
                }
            }
        }

        NativeRechargeSection(title = "支付方式") {
            when {
                data == null -> Text(
                    text = "支付方式加载中，可先填写充值金额",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                data.paymentMethods.isEmpty() -> {
                    Text(
                        text = "尚未绑定可用的免密支付方式。请先在学校支付页面完成绑定。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onManagePaymentMethods,
                        enabled = !isSubmitting
                    ) {
                        Text("管理免密支付方式")
                    }
                }

                else -> {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSubmitting) {
                                    paymentMenuExpanded = true
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("扣款方式")
                            Text(
                                selectedMethod?.name.orEmpty(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = paymentMenuExpanded,
                            onDismissRequest = { paymentMenuExpanded = false }
                        ) {
                            data.paymentMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.name) },
                                    onClick = {
                                        selectedPaymentMethodIndex = method.pageIndex
                                        paymentMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = onManagePaymentMethods,
                        enabled = !isSubmitting
                    ) {
                        Text("管理支付方式")
                    }
                }
            }
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onSubmit(amount, selectedPaymentMethodIndex)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = canSubmit
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("前往招商银行支付")
            }
        }

        Text(
            text = "充值金额将先进入过渡余额，刷卡后转入校园卡。银行授权、验证码等敏感操作只在官方页面中完成。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NativeRechargeSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun NativeRechargeInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NativeRechargeMessageCard(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction) { Text(actionText) }
    }
}

private const val CMB_RECHARGE_MAX_AMOUNT = 1_000.0
