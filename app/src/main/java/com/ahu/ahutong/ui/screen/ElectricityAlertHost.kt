package com.ahu.ahutong.ui.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.state.ElectricityAlertViewModel
import com.ahu.ahutong.ui.state.LoginState
import kotlinx.coroutines.launch

internal fun canShowElectricityAlert(resumed: Boolean, usableSession: Boolean, qrVisible: Boolean): Boolean =
    resumed && usableSession && !qrVisible

/** The reminder lives above navigation, so it can appear on any authenticated screen. */
@Composable
internal fun ElectricityAlertHost(
    navController: NavHostController,
    route: String?,
    loginState: LoginState,
    paymentQrCommands: PaymentQrOpenCommandStore,
    viewModel: ElectricityAlertViewModel
) {
    val status by AhuSessionState.status.collectAsState()
    val qrVisible by paymentQrCommands.visible.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val rooms by viewModel.presented.collectAsState()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val usable = route != null && route !in setOf("splash", "login", "setup") &&
        loginState !in setOf(LoginState.InProgress, LoginState.WebVerification, LoginState.Failed) &&
        status != AhuSessionState.Status.Expired && !AHUCache.getMockData()
    val currentUsable by rememberUpdatedState(usable)

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    scope.launch { viewModel.resumeSession(currentUsable) }
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(route, loginState, status) { viewModel.resumeSession(usable) }
    val allowed = canShowElectricityAlert(resumed, usable, qrVisible)
    LaunchedEffect(pending, allowed) { if (allowed && pending.isNotEmpty()) viewModel.showPending() }

    if (allowed && rooms.isNotEmpty()) {
        var selected by remember(rooms) { mutableStateOf(rooms.first()) }
        AppDialog(
            title = "电费余额提醒",
            onDismiss = viewModel::dismiss,
            actions = listOf(
                AppDialogAction("稍后", viewModel::dismiss),
                AppDialogAction("是，去充值", {
                    viewModel.recharge(selected)
                    navController.navigate("electricity_pay") { launchSingleTop = true }
                }, AppDialogActionStyle.Primary)
            ),
            content = {
                if (rooms.size > 1) {
                    AppSelectField(
                        label = "需要充值的房间", selected = selected,
                        options = rooms.map { AppSelectOption(it, it.label) }, onSelected = { selected = it }
                    )
                }
                Text("${selected.label}电费余额可能不足以继续使用${selected.thresholdDays}天，是否充值？")
            }
        )
    }
}
