package com.ahu.ahutong.ui.screen.main.home


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.WindowManager
import dagger.hilt.android.EntryPointAccessors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.components.liquidGlassSurface
import com.ahu.ahutong.ui.components.liquidGlassTint
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.personalization.prefetch.PaymentQrCommandEntryPoint
import com.ahu.ahutong.personalization.runtime.BehaviorRuntimeEntryPoint
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.ActionSource
import com.kyant.backdrop.Backdrop
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CampusCard(
    balance: Double,
    transitionBalance: Double,
    onRefreshBalance: () -> Unit,
    navController: NavController,
    enabled: Boolean = true,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context = context) }
    val paymentQrCommands = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PaymentQrCommandEntryPoint::class.java
        ).paymentQrOpenCommandStore()
    }
    val behaviorRuntime = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BehaviorRuntimeEntryPoint::class.java
        ).behaviorPredictionRuntime()
    }

    val pref by preferencesManager.showQRCode.collectAsState(initial = false)
    val behaviorDiagnostics by behaviorRuntime.diagnostics.collectAsState()

    var isQrcode by remember { mutableStateOf(false) }
    val paymentQrCommand by paymentQrCommands.command.collectAsState()

    LaunchedEffect(pref) {
        if (!pref) isQrcode = false
    }

    LaunchedEffect(pref, behaviorDiagnostics.profileActive) {
        if (pref && behaviorDiagnostics.profileActive) {
            isQrcode = behaviorRuntime.authorizeUserPreferencePaymentQr()
        }
    }

    LaunchedEffect(paymentQrCommand?.commandId) {
        val command = paymentQrCommand ?: return@LaunchedEffect
        if (paymentQrCommands.consume(command.commandId) != null) isQrcode = true
    }

    LaunchedEffect(isQrcode) {
        if (AHUCache.isLogin() && isQrcode) {
            onRefreshBalance()
        }
    }


    val campusShape = SmoothRoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .then(
                if (isRadiantUi) {
                    // RadiantUI：保留液态玻璃与阴影
                    Modifier.liquidGlassSurface(backdrop, campusShape, liquidGlassTint())
                } else {
                    // ORIGINAL / LIQUID_GLASS：纯色卡片，不带玻璃与阴影
                    Modifier
                        .clip(campusShape)
                        .background(100.n1 withNight 20.n1)
                }
            )
    ) {
        AnimatedContent(
            targetState = isQrcode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith fadeOut(animationSpec = tween(durationMillis = 150)))
                    .using(SizeTransform(clip = true))
            },
            contentAlignment = Alignment.TopStart,
            label = "campus-card-qrcode"
        ) { showQrcode ->
            if (showQrcode) {
                QRcodeView(
                    balance = balance,
                    onBack = {
                        isQrcode = false
                    },
                    backdrop = backdrop
                )
            } else {
                CardView(
                    balance = balance,
                    transitionBalance = transitionBalance,
                    onClick = {
                        behaviorRuntime.recordActionIntentAsync(AppActionId.OPEN_PAYMENT_QR, ActionSource.ORGANIC)
                        isQrcode = true
                    },
                    navController = navController,
                    enabled = enabled,
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isRadiantUi) 78.dp else 140.dp)
                )
            }
        }
    }


}


@Composable
private fun CardView(
    balance: Double,
    transitionBalance: Double,
    onClick: () -> Unit,
    navController: NavController,
    enabled: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {


    val shape = SmoothRoundedCornerShape(24.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .then(
                    if (enabled) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.card_money),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "¥ ${formatCampusCardBalance(balance)}",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }


        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(96.n1 withNight 10.n1)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (isRadiantUi) {
                        Modifier.width(76.dp)
                    } else {
                        Modifier.padding(16.dp)
                    }
                )
                .then(
                    if (enabled) {
                        Modifier.clickable {
                            val route = if (AHUCache.isCmbCardRechargePreferred()) {
                                "cmb_card_recharge"
                            } else {
                                "card_balance_deposit"
                            }
                            navController.navigate(route)
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isRadiantUi) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_income),
                        contentDescription = "充值",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "充值",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = "充\n值",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

}

private fun formatCampusCardBalance(balance: Double): String {
    if (!balance.isFinite()) return "--"
    return String.format(Locale.CHINA, "%.2f", balance)
}


@Composable
private fun QRcodeView(balance: Double, onBack: () -> Unit, backdrop: Backdrop) {
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
    val qrcodeBitmap by discoveryViewModel.qrcode.collectAsState()
    val finished by discoveryViewModel.state.collectAsState()

    var showFullScreen by remember {
        mutableStateOf(false)
    }

    val activity = androidx.activity.compose.LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val behaviorRuntime = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BehaviorRuntimeEntryPoint::class.java
        ).behaviorPredictionRuntime()
    }

    DisposableEffect(Unit) {
        behaviorRuntime.setInlineSensitiveUiVisible(true)
        onDispose {
            behaviorRuntime.setInlineSensitiveUiVisible(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> discoveryViewModel.clearQrCode()
                Lifecycle.Event.ON_START -> if (AHUCache.isLogin()) discoveryViewModel.loadQrCode()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            discoveryViewModel.clearQrCode()
        }
    }

    if (AHUCache.isLogin()) {
        LaunchedEffect(Unit) {
            discoveryViewModel.loadQrCode()
        }
    }

    // 放大二维码时亮度拉满，关闭时恢复
    if (showFullScreen) {
        DisposableEffect(Unit) {
            val window = activity?.window
            val layoutParams = window?.attributes

            // 保存原始亮度
            val originalBrightness =
                layoutParams?.screenBrightness ?: -1f

            // 设置最高亮度
            layoutParams?.screenBrightness = 1f
            window?.attributes = layoutParams

            onDispose {
                // 恢复原始亮度
                layoutParams?.screenBrightness =
                    originalBrightness
                window?.attributes = layoutParams
            }
        }
    }

    val shape = SmoothRoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 20.dp
                ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 顶部工具栏：左返回，右放大
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // 左侧返回
            IconButton(
                onClick = {
                    discoveryViewModel.clearQrCode()
                    onBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }

            // 右侧全屏
            IconButton(
                onClick = {
                    showFullScreen = true
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "放大"
                )
            }
        }
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(SmoothRoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    1.dp,
                    Color.Gray,
                    SmoothRoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (finished) {
                qrcodeBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                behaviorRuntime.recordActionIntentAsync(AppActionId.REFRESH_PAYMENT_QR, ActionSource.ORGANIC)
                                discoveryViewModel.loadQrCode(forceRefresh = true)
                                discoveryViewModel.refreshCardBalance()
                            }
                            .padding(8.dp)
                    )
                } ?: Text(
                    text = "加载失败"
                )
            } else {
                CircularProgressIndicator()
            }
        }

        Text(
            text = "¥ ${formatCampusCardBalance(balance)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (LocalIsLiquidGlassEnabled.current) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (showFullScreen) {
            Dialog(
                onDismissRequest = {
                    showFullScreen = false
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            showFullScreen = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    qrcodeBitmap?.let {
                        Box(
                            modifier = Modifier
                                .size(360.dp)
                                .clip(
                                    SmoothRoundedCornerShape(
                                        24.dp
                                    )
                                )
                                .background(Color.White)
                                .border(
                                    1.dp,
                                    Color.Gray,
                                    SmoothRoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    behaviorRuntime.recordActionIntentAsync(AppActionId.REFRESH_PAYMENT_QR, ActionSource.ORGANIC)
                                    discoveryViewModel.loadQrCode(forceRefresh = true)
                                    discoveryViewModel.refreshCardBalance()
                                }
                                .padding(20.dp)
                        ) {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription =
                                    "Full QR Code",
                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
