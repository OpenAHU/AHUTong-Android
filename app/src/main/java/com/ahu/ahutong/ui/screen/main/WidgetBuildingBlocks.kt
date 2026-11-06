package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ahu.ahutong.ui.haptics.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.haptics.HapticDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.navigation.NavHostController
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.util.Log
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.utils.FileUtils
import com.ahu.ahutong.R
import com.ahu.ahutong.appwidget.ScheduleAppWidgetReceiver
import com.ahu.ahutong.appwidget.ScheduleWidgetPreviewContent
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.appWallpaperFrostedSurface
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetSpec
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Edit


@Composable
internal fun DesktopScheduleWidgetCard() {
    if (!AHUCache.canUseUndergraduateAcademics()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appLiquidGlassSurface(
                shape = SmoothRoundedCornerShape(32.dp),
                fallbackColor = 100.n1 withNight 30.n1
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "添加桌面课表微件",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            style = MaterialTheme.typography.titleMedium
        )
        DesktopScheduleWidgetPreview(Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp))
        AppButton(
            onClick = {
                scope.launch {
                    GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                        ScheduleAppWidgetReceiver::class.java
                    )
                }
            },
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
        ) {
            Text("添加", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Keep the example compact and reuse Glance, so the preview follows the actual desktop style. */
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
@Composable
internal fun DesktopScheduleWidgetPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val renderer = remember { androidx.glance.appwidget.GlanceRemoteViews() }
    val previewHeight = desktopSchedulePreviewHeight(configuration.fontScale)
    BoxWithConstraints(modifier.widthIn(max = 360.dp).fillMaxWidth().height(previewHeight)) {
        val size = androidx.compose.ui.unit.DpSize(maxWidth, previewHeight)
        val preview by produceState<Result<android.widget.RemoteViews>?>(null, context, size, configuration.uiMode) {
            value = try {
                Result.success(renderer.compose(context, size) { ScheduleWidgetPreviewContent() }.remoteViews)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
        val remoteViews = preview?.getOrNull()
        if (preview == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        } else if (remoteViews == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("课表微件预览暂不可用", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { android.widget.FrameLayout(it) },
                update = { host ->
                    host.removeAllViews()
                    host.addView(remoteViews.apply(context, host), android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Include both course detail rows and let accessibility font sizes grow without a height cap. */
internal fun desktopSchedulePreviewHeight(fontScale: Float) = (208f * fontScale.coerceAtLeast(1f)).dp

/**
 * 「全部小工具」页的分类网格卡：图标 + 名字横排矮宽卡（48dp 高，图标 20dp）。
 *
 * 卡面无自定义背景时保持无色透明 + 细边框（与旧版 ToolItem 的零填充观感一致，只多一圈描边）；
 * 开启自定义背景时补一层磨砂底纹（surfaceContainer ×0.45，近白/近黑随深浅模式），
 * 否则文字压在图上看不清。口径同 AppCard，但贴图更薄以适配 48dp 小卡。
 * 点击反馈用 `indication = null`（照 RadiantCardImpl）：不 clip 不垫色，无玻璃阴影裁切问题。
 */
@Composable
internal fun CategorizedToolCard(
    spec: HomeWidgetSpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .appWallpaperFrostedSurface(
                shape = SmoothRoundedCornerShape(16.dp),
                tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
            )
            .border(
                width = 1.dp,
                color = 90.n1 withNight 30.n1,
                shape = SmoothRoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val iconBytes = spec.iconBytes
        val bitmap = if (iconBytes != null) {
            val decoded by produceState<ImageBitmap?>(null, iconBytes) {
                value = withContext(Dispatchers.Default) {
                    android.graphics.BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)?.asImageBitmap()
                }
            }
            decoded
        } else {
            null
        }
        when {
            bitmap != null -> androidx.compose.foundation.Image(
                bitmap = bitmap,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
            // iconId=0 是插件没带资源图标的形态（iconBytes 优先，双缺失时兜底）
            spec.iconId != 0 -> Icon(
                painter = painterResource(id = spec.iconId),
                modifier = Modifier.size(20.dp),
                contentDescription = null,
                tint = spec.tint
            )
            else -> Icon(
                painter = painterResource(id = com.ahu.ahutong.R.drawable.ic_round_business_24),
                modifier = Modifier.size(20.dp),
                contentDescription = null,
                tint = spec.tint
            )
        }
        Text(
            text = spec.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
