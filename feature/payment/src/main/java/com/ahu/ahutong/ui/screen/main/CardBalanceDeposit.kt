package com.ahu.ahutong.ui.screen.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.feature.payment.R
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuInsetCard
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.ahu.ahutong.ui.state.CardAccountState
import com.ahu.ahutong.ui.state.CardBalanceDepositViewModel
import com.ahu.ahutong.ui.state.PaymentState
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight


private const val ALIPAY_CAMPUS_CARD_SCHEME =
    "alipays://platformapi/startapp?appId=2019090967125695&page=pages%2Findex%2Findex&chInfo=ch_share__chsub_CopyLink"
private const val ALIPAY_CAMPUS_CARD_FALLBACK_URL = "https://www.wmslz.com/s/M6KARh485j3"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardBalanceDeposit(
    viewModel: CardBalanceDepositViewModel = hiltViewModel(),
    mockRefreshRevision: Long = 0L,
) {

    var amount by remember { mutableStateOf("") }

    val cardInfo = viewModel.cardInfo.collectAsState()
    val accountState by viewModel.accountState.collectAsState()

    val paymentState by viewModel.paymentState.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val campusCardUserName = remember { viewModel.getCurrentUserName().orEmpty() }
    val campusCardStudentId = remember { viewModel.getCurrentUserId().orEmpty() }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && viewModel.isMockMode()) {
            viewModel.load()
        }
    }

    AhuScreen(clearBottomNav = false) {
        AhuPageHeader(title = stringResource(R.string.campus_card_recharge))

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.campus_card_account),
                    style = MaterialTheme.typography.titleMedium
                )

                when (val state = accountState) {
                    CardAccountState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AhuColors.onSurface.copy(alpha = 0.55f)
                        )
                    }

                    is CardAccountState.Ready -> {
                        val accountInfo = state.cardInfo.data.card.getOrNull(0)?.accinfo
                            ?.getOrNull(0)
                        Text(
                            text = accountInfo?.let { "${it.name} ${it.type}" } ?: "--"
                        )
                    }

                    is CardAccountState.Error -> {
                        Text(
                            text = stringResource(R.string.load_failed),
                            color = Color.Red
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.account_balance), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = cardInfo.value?.data?.card?.getOrNull(0)
                        ?.accinfo?.getOrNull(0)?.balance?.let {
                            stringResource(R.string.balance_yuan, it / 100.0)
                        }
                        ?: stringResource(R.string.balance_placeholder),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.recharge_amount),
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
                            targetValue = when (paymentState) {
                                PaymentState.Idle -> AhuColors.primaryAction
                                PaymentState.Loading -> 70.a1 withNight 60.a1
                                is PaymentState.Error -> Color.Red
                                is PaymentState.Success -> 70.a1 withNight 60.a1
                            },
                            label = "paymentStateBg"
                        ).value
                    )
                    .animateContentSize(spring(stiffness = Spring.StiffnessLow))
            ) {
                when (val state = paymentState) {
                    PaymentState.Idle -> {
                        CompositionLocalProvider(LocalIndication provides ripple(color = AhuColors.onPrimaryAction)) {
                            Text(
                                text = stringResource(R.string.confirm),
                                modifier = Modifier
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            if (amount.isNotEmpty()) {
                                                showConfirmDialog = true
                                            }
                                        }
                                    )
                                    .padding(24.dp, 16.dp),
                                color = AhuColors.onPrimaryAction,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    PaymentState.Loading -> {
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

                    is PaymentState.Error -> {
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
                                text = stringResource(R.string.payment_failed_with_error, state.message),
                                modifier = Modifier.padding(4.dp),
                                color = 100.n1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                    is PaymentState.Success -> {
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
                                text = stringResource(R.string.payment_success_order_compact, state.orderId),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable {
                                        viewModel.resetPaymentState()
                                    },
                                color = 100.n1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        }

        if (showConfirmDialog) {
            val notObtained = stringResource(R.string.not_obtained)
            AhuDialog(onDismissRequest = { showConfirmDialog = false }) {
                Text(
                    text = stringResource(R.string.confirm_payment),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = AhuColors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.payment_method_description, amount),
                        color = AhuColors.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(
                            R.string.name_and_student_id,
                            campusCardUserName.ifBlank { notObtained },
                            campusCardStudentId.ifBlank { notObtained }
                        ),
                        color = AhuColors.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (campusCardUserName.isBlank() || campusCardStudentId.isBlank()) {
                            stringResource(R.string.identity_missing_manual)
                        } else {
                            stringResource(R.string.identity_copy_hint)
                        },
                        color = AhuColors.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AhuPrimaryButton(
                        text = stringResource(R.string.alipay_payment),
                        onClick = {
                            val identityState = copyCampusCardIdentity(
                                context = context,
                                name = campusCardUserName,
                                studentId = campusCardStudentId
                            )
                            val message = when (identityState) {
                                CampusCardIdentityCopyState.Complete ->
                                    context.getString(R.string.copied_name_and_student_id)
                                CampusCardIdentityCopyState.Partial ->
                                    context.getString(R.string.copied_partial_info)
                                CampusCardIdentityCopyState.Empty ->
                                    context.getString(R.string.no_local_identity_manual)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            openAlipayCampusCard(context)
                            showConfirmDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AhuPrimaryButton(
                        text = stringResource(R.string.bank_card_payment),
                        onClick = {
                            if (accountState is CardAccountState.Ready) {
                                viewModel.charge(amount)
                                showConfirmDialog = false
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.campus_card_still_loading),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = AhuColors.cardStrong,
                        contentColor = AhuColors.onSurface,
                    )
                    AhuPrimaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showConfirmDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = AhuColors.chipUnselected,
                        contentColor = AhuColors.onSurface,
                    )
                }
            }
        }
    }
}

private enum class CampusCardIdentityCopyState {
    Complete,
    Partial,
    Empty
}

private fun copyCampusCardIdentity(
    context: Context,
    name: String,
    studentId: String
): CampusCardIdentityCopyState {
    val trimmedName = name.trim()
    val trimmedStudentId = studentId.trim()
    if (trimmedName.isEmpty() && trimmedStudentId.isEmpty()) {
        return CampusCardIdentityCopyState.Empty
    }

    val clipText = context.getString(R.string.name_and_student_id, trimmedName, trimmedStudentId)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.clipboard_label_campus_card_identity),
            clipText
        )
    )

    return if (trimmedName.isNotEmpty() && trimmedStudentId.isNotEmpty()) {
        CampusCardIdentityCopyState.Complete
    } else {
        CampusCardIdentityCopyState.Partial
    }
}

private fun openAlipayCampusCard(context: Context) {
    val openedAlipay = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_CAMPUS_CARD_SCHEME))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    if (openedAlipay) return

    val openedFallback = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_CAMPUS_CARD_FALLBACK_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    if (!openedFallback) {
        Toast.makeText(
            context,
            context.getString(R.string.cannot_open_alipay_campus_card),
            Toast.LENGTH_SHORT
        ).show()
    }
}
