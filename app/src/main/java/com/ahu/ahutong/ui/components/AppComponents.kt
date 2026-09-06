package com.ahu.ahutong.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults as MaterialButtonDefaults
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults as MaterialCardDefaults
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton as MaterialFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet as MaterialModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors as MiuixButtonColors
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InputField as MiuixSearchInputField
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults as MiuixProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperBottomSheet as MiuixSuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Shared geometry for app-level controls and surfaces. */
object AppComponentTokens {
    val TouchTarget = 48.dp
    val SearchFieldHeight = 56.dp
    val ChipHeight = 40.dp
    val HeaderHorizontalPadding = 20.dp
    val HeaderVerticalPadding = 14.dp
    val ControlShape = SmoothRoundedCornerShape(24.dp)
    val CardShape = SmoothRoundedCornerShape(24.dp)
    val LargeCardShape = SmoothRoundedCornerShape(32.dp)
    val DialogShape = SmoothRoundedCornerShape(28.dp)
    val DialogMaxWidth = 560.dp
}

enum class AppButtonVariant {
    Primary,
    Secondary,
    Destructive
}

data class AppSelectOption<T>(
    val value: T,
    val label: String
)

/** A theme-native content card without leaking Material ripple or geometry into other themes. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppComponentTokens.CardShape,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val uiTheme = LocalAppUiTheme.current
    val haptic = LocalHapticFeedback.current
    val action = onClick?.let { click ->
        {
            if (uiTheme == AppUiTheme.MIUIX) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            click()
        }
    }

    when (uiTheme) {
        AppUiTheme.MIUIX -> {
            if (action == null || !enabled) {
                MiuixCard(
                    modifier = modifier,
                    cornerRadius = 16.dp,
                    insideMargin = contentPadding,
                    content = content
                )
            } else {
                MiuixCard(
                    modifier = modifier,
                    cornerRadius = 16.dp,
                    insideMargin = contentPadding,
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = action,
                    content = content
                )
            }
        }

        AppUiTheme.MATERIAL -> {
            if (action == null) {
                MaterialCard(
                    modifier = modifier,
                    shape = shape,
                    colors = MaterialCardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(contentPadding), content = content)
                }
            } else {
                MaterialCard(
                    onClick = action,
                    modifier = modifier,
                    enabled = enabled,
                    shape = shape,
                    colors = MaterialCardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(contentPadding), content = content)
                }
            }
        }

        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> {
            Column(
                modifier = modifier
                    .appLiquidGlassSurface(
                        shape = shape,
                        fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                        level = LiquidGlassSurfaceLevel.Panel,
                        backdrop = backdrop,
                        backdropSamplingEnabled = true
                    )
                    .then(
                        if (action != null) {
                            Modifier.clickable(
                                enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = action
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

@Composable
fun AppHeaderIconButton(
    imageVector: ImageVector,
    miuixImageVector: ImageVector = imageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    tint: Color? = null
) {
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> {
            val haptic = LocalHapticFeedback.current
            MiuixIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                modifier = modifier.size(AppComponentTokens.TouchTarget),
                minWidth = AppComponentTokens.TouchTarget,
                minHeight = AppComponentTokens.TouchTarget,
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer
            ) {
                MiuixIcon(
                    imageVector = miuixImageVector,
                    contentDescription = contentDescription,
                    tint = tint ?: MiuixTheme.colorScheme.onSurface
                )
            }
            return
        }
        AppUiTheme.MATERIAL -> {
            IconButton(
                onClick = onClick,
                modifier = modifier.size(AppComponentTokens.TouchTarget)
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = tint ?: MaterialTheme.colorScheme.onSurface
                )
            }
            return
        }
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> Unit
    }
    Box(
        modifier = modifier
            .size(AppComponentTokens.TouchTarget)
            .appLiquidGlassSurface(
                shape = AppComponentTokens.ControlShape,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                level = LiquidGlassSurfaceLevel.Control,
                backdrop = backdrop
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AppPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
    horizontalPadding: Dp = AppComponentTokens.HeaderHorizontalPadding,
    verticalPadding: Dp = AppComponentTokens.HeaderVerticalPadding,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val haptic = LocalHapticFeedback.current
        MiuixTopAppBar(
            title = title,
            largeTitle = title,
            modifier = modifier,
            navigationIcon = {
                onBack?.let { callback ->
                    MiuixIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            callback()
                        }
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            actions = actions
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onBack?.let {
            AppHeaderIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = it,
                backdrop = backdrop
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = if (onBack == null) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.SemiBold
        )
        actions()
    }
}

/** Page shell for screens that own their scrolling container. */
@Composable
fun AppPageLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    if (LocalAppUiTheme.current != AppUiTheme.MIUIX) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            AppPageHeader(
                title = title,
                onBack = onBack,
                actions = actions
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content
            )
        }
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    val haptic = LocalHapticFeedback.current
    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MiuixTopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    onBack?.let { callback ->
                        MiuixIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callback()
                            }
                        ) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Useful.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = actions
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding() + 20.dp)
                .navigationBarsPadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic(),
            content = content
        )
    }
}

/**
 * Shared vertically scrolling page shell. Miuix owns the collapsible title and navigation
 * controls; Material and Liquid Glass retain their own in-content header treatment.
 */
@Composable
fun AppScrollablePageLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    bottomPadding: Dp = 112.dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val uiTheme = LocalAppUiTheme.current
    if (uiTheme != AppUiTheme.MIUIX) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            AppPageHeader(
                title = title,
                onBack = onBack,
                backdrop = backdrop,
                actions = actions
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState, enabled = scrollEnabled)
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = content
            )
        }
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    val haptic = LocalHapticFeedback.current
    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MiuixTopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    onBack?.let { callback ->
                        MiuixIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callback()
                            }
                        ) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Useful.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = actions
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .verticalScroll(scrollState, enabled = scrollEnabled)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = paddingValues.calculateTopPadding() + 20.dp,
                        bottom = bottomPadding
                    )
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = content
            )
        }
    }
}

/** Lazy counterpart of [AppScrollablePageLayout], avoiding a nested scroll container. */
@Composable
fun AppLazyPageLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 112.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val uiTheme = LocalAppUiTheme.current
    if (uiTheme != AppUiTheme.MIUIX) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            AppPageHeader(title = title, onBack = onBack, actions = actions)
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    val haptic = LocalHapticFeedback.current
    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MiuixTopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    onBack?.let { callback ->
                        MiuixIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                callback()
                            }
                        ) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Useful.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = actions
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 20.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

/** Theme-native indeterminate loading control. */
@Composable
fun AppCircularProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    strokeWidth: Dp = 4.dp,
    color: Color? = null
) {
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> MiuixCircularProgressIndicator(
            progress = progress?.invoke(),
            modifier = modifier,
            size = size,
            strokeWidth = strokeWidth,
            colors = MiuixProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color ?: MiuixTheme.colorScheme.primary,
                backgroundColor = (color ?: MiuixTheme.colorScheme.primary).copy(alpha = 0.16f)
            )
        )
        AppUiTheme.MATERIAL -> if (progress == null) {
            MaterialCircularProgressIndicator(
                modifier = modifier.size(size),
                color = color ?: MaterialTheme.colorScheme.primary,
                strokeWidth = strokeWidth
            )
        } else {
            MaterialCircularProgressIndicator(
                progress = progress,
                modifier = modifier.size(size),
                color = color ?: MaterialTheme.colorScheme.primary,
                strokeWidth = strokeWidth
            )
        }
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> LiquidGlassProgressIndicator(
            progress = progress?.invoke(),
            modifier = modifier,
            size = size,
            strokeWidth = strokeWidth,
            color = color ?: MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LiquidGlassProgressIndicator(
    progress: Float?,
    modifier: Modifier,
    size: Dp,
    strokeWidth: Dp,
    color: Color
) {
    if (progress != null) {
        Canvas(modifier = modifier.size(size)) {
            val width = strokeWidth.toPx()
            drawCircle(
                color = color.copy(alpha = 0.16f),
                radius = (this.size.minDimension - width) / 2f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "liquid-loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "liquid-loading-rotation"
    )
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val segmentStart = radius * 0.48f
        val segmentEnd = radius * 0.82f
        repeat(12) { index ->
            val alpha = 0.16f + 0.84f * ((index + 1) / 12f)
            rotate(degrees = rotation + index * 30f) {
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = androidx.compose.ui.geometry.Offset(center.x, center.y - segmentEnd),
                    end = androidx.compose.ui.geometry.Offset(center.x, center.y - segmentStart),
                    strokeWidth = strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = content
        )
        AppUiTheme.MATERIAL -> MaterialFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = content
        )
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> Box(
            modifier = modifier
                .size(60.dp)
                .appLiquidGlassSurface(
                    shape = AppComponentTokens.ControlShape,
                    fallbackColor = MaterialTheme.colorScheme.primaryContainer,
                    level = LiquidGlassSurfaceLevel.Floating,
                    backdrop = LocalLiquidGlassAmbientBackdrop.current,
                    backdropSamplingEnabled = false
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

@Composable
fun AppSearchHeader(
    title: String,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    edgePadding: Dp = AppComponentTokens.HeaderHorizontalPadding,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (!searchActive) {
        AppPageHeader(
            title = title,
            modifier = modifier,
            horizontalPadding = edgePadding,
            actions = {
                AppHeaderIconButton(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    onClick = onSearchOpen
                )
                actions()
            }
        )
        return
    }

    BackHandler(onBack = onSearchClose)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = edgePadding,
                vertical = AppComponentTokens.HeaderVerticalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppHeaderIconButton(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "关闭搜索",
            onClick = onSearchClose
        )
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(AppComponentTokens.SearchFieldHeight),
            singleLine = true,
            placeholder = { Text(placeholder) },
            shape = AppComponentTokens.ControlShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            trailingIcon = {
                when {
                    query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清空")
                    }
                    onSearch != null -> IconButton(onClick = onSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {}
) {
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        MiuixSearchInputField(
            query = value,
            onQueryChange = onValueChange,
            label = placeholder,
            onSearch = onSearch,
            expanded = true,
            onExpandedChange = {},
            modifier = modifier
        )
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = AppComponentTokens.ControlShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        colors = if (LocalAppUiTheme.current.usesLiquidGlass) {
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        }
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            useLabelAsPlaceholder = true,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation
        )
        return
    }
    val liquid = LocalAppUiTheme.current.usesLiquidGlass
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        label = { Text(label) },
        shape = AppComponentTokens.ControlShape,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = if (liquid) {
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        }
    )
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surfaceColor = when (variant) {
        AppButtonVariant.Primary -> colors.primaryContainer
        AppButtonVariant.Secondary -> colors.surfaceContainerHigh
        AppButtonVariant.Destructive -> colors.errorContainer
    }
    val contentColor = when (variant) {
        AppButtonVariant.Primary -> colors.onPrimaryContainer
        AppButtonVariant.Secondary -> colors.onSurface
        AppButtonVariant.Destructive -> colors.onErrorContainer
    }
    val tint = when (variant) {
        AppButtonVariant.Primary -> colors.primary
        AppButtonVariant.Secondary -> colors.secondary
        AppButtonVariant.Destructive -> colors.error
    }

    val liquidContent: @Composable RowScope.() -> Unit = {
        CompositionLocalProvider(
            LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.72f)
        ) { content() }
    }
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> {
            val haptic = LocalHapticFeedback.current
            val miuixColor = when (variant) {
                AppButtonVariant.Primary -> MiuixTheme.colorScheme.primary
                AppButtonVariant.Secondary -> MiuixTheme.colorScheme.secondaryVariant
                AppButtonVariant.Destructive -> colors.error
            }
            val miuixDisabledColor = when (variant) {
                AppButtonVariant.Primary -> MiuixTheme.colorScheme.disabledPrimaryButton
                else -> MiuixTheme.colorScheme.disabledSecondaryVariant
            }
            val miuixContentColor = when (variant) {
                AppButtonVariant.Primary -> MiuixTheme.colorScheme.onPrimary
                AppButtonVariant.Secondary -> MiuixTheme.colorScheme.onSecondaryVariant
                AppButtonVariant.Destructive -> colors.onError
            }
            MiuixButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                modifier = modifier,
                enabled = enabled,
                minHeight = AppComponentTokens.TouchTarget,
                colors = MiuixButtonColors(miuixColor, miuixDisabledColor),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides miuixContentColor.copy(
                        alpha = if (enabled) 1f else 0.60f
                    )
                ) { content() }
            }
        }
        AppUiTheme.MATERIAL -> {
            val materialModifier = modifier.heightIn(min = AppComponentTokens.TouchTarget)
            when (variant) {
                AppButtonVariant.Secondary -> FilledTonalButton(
                    onClick = onClick,
                    modifier = materialModifier,
                    enabled = enabled,
                    content = content
                )
                AppButtonVariant.Primary, AppButtonVariant.Destructive -> MaterialButton(
                    onClick = onClick,
                    modifier = materialModifier,
                    enabled = enabled,
                    colors = if (variant == AppButtonVariant.Destructive) {
                        MaterialButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError
                        )
                    } else {
                        MaterialButtonDefaults.buttonColors()
                    },
                    content = content
                )
            }
        }
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> LiquidButton(
            onClick = onClick,
            backdrop = LocalLiquidGlassAmbientBackdrop.current,
            modifier = modifier.heightIn(min = AppComponentTokens.TouchTarget),
            enabled = enabled,
            tint = tint,
            surfaceColor = surfaceColor,
            content = liquidContent
        )
    }
}

@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> {
            MiuixSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier,
                enabled = enabled
            )
        }
        AppUiTheme.MATERIAL -> MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = LocalLiquidGlassAmbientBackdrop.current,
            modifier = modifier,
            userInputEnabled = enabled,
            contentDescription = contentDescription
        )
    }
}

/** Dispatches to three independent controls so one design system cannot leak into another. */
@Composable
fun <T> AppSelectField(
    label: String,
    selected: T?,
    options: List<AppSelectOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "请选择",
    enabled: Boolean = true,
    valueTextAlign: TextAlign = TextAlign.Start,
    miuixInsideMargin: PaddingValues = PaddingValues(16.dp),
    miuixStandalone: Boolean = false,
    liquidLabelWeight: Float = 1f,
    liquidValueWeight: Float = 1f
) {
    val selectedIndex = remember(options, selected) {
        options.indexOfFirst { it.value == selected }
    }
    val selectedLabel = options.getOrNull(selectedIndex)?.label ?: placeholder
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> MiuixSelectField(
            label = label,
            selectedIndex = selectedIndex,
            options = options,
            onSelected = onSelected,
            modifier = modifier,
            enabled = enabled,
            insideMargin = miuixInsideMargin,
            standalone = miuixStandalone
        )
        AppUiTheme.MATERIAL -> MaterialSelectField(
            label = label,
            selected = selected,
            selectedLabel = selectedLabel,
            options = options,
            onSelected = onSelected,
            modifier = modifier,
            enabled = enabled,
            valueTextAlign = valueTextAlign
        )
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> LiquidGlassSelectField(
            label = label,
            selected = selected,
            selectedLabel = selectedLabel,
            options = options,
            onSelected = onSelected,
            modifier = modifier,
            enabled = enabled,
            valueTextAlign = valueTextAlign,
            labelWeight = liquidLabelWeight,
            valueWeight = liquidValueWeight
        )
    }
}

@Composable
private fun <T> MiuixSelectField(
    label: String,
    selectedIndex: Int,
    options: List<AppSelectOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    insideMargin: PaddingValues,
    standalone: Boolean
) {
    val optionLabels = remember(options) { options.map(AppSelectOption<T>::label) }
    val haptic = LocalHapticFeedback.current
    val dropdown: @Composable (Modifier) -> Unit = { dropdownModifier ->
        SuperDropdown(
            items = optionLabels,
            selectedIndex = selectedIndex.coerceAtLeast(0),
            title = label,
            modifier = dropdownModifier.fillMaxWidth(),
            insideMargin = insideMargin,
            enabled = enabled,
            showValue = selectedIndex >= 0,
            onSelectedIndexChange = { index ->
                options.getOrNull(index)?.let { option ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelected(option.value)
                }
            }
        )
    }
    if (standalone) {
        MiuixSurface(
            modifier = modifier.fillMaxWidth(),
            shape = SmoothRoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surfaceContainer
        ) {
            dropdown(Modifier)
        }
    } else {
        dropdown(modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> MaterialSelectField(
    label: String,
    selected: T?,
    selectedLabel: String,
    options: List<AppSelectOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueTextAlign: TextAlign
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = valueTextAlign),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = true,
            shape = MenuDefaults.shape,
            containerColor = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = valueTextAlign
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    ),
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> LiquidGlassSelectField(
    label: String,
    selected: T?,
    selectedLabel: String,
    options: List<AppSelectOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueTextAlign: TextAlign,
    labelWeight: Float,
    valueWeight: Float
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    var fieldBoundsInWindow by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }
    val popupVisibility = remember { MutableTransitionState(false) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val popupBackdrop = LocalLiquidGlassContentBackdrop.current
    val popupShape = SmoothRoundedCornerShape(20.dp)
    val popupGapPx = with(density) { 6.dp.roundToPx() }
    val popupWidth = with(density) { (anchorWidthPx / 2).toDp() }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "liquid dropdown arrow"
    )
    val popupPositionProvider = remember(popupGapPx, fieldBoundsInWindow) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val fieldBounds = fieldBoundsInWindow.takeIf { it.width > 0 && it.height > 0 }
                    ?: anchorBounds
                val preferredX = fieldBounds.right - popupContentSize.width
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val x = preferredX.coerceIn(0, maxX)
                val below = fieldBounds.bottom + popupGapPx
                val above = fieldBounds.top - popupContentSize.height - popupGapPx
                val y = if (below + popupContentSize.height <= windowSize.height) {
                    below
                } else {
                    above.coerceAtLeast(0)
                }
                return IntOffset(x, y)
            }
        }
    }
    LaunchedEffect(expanded) {
        popupVisibility.targetState = expanded
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    anchorWidthPx = coordinates.size.width
                    val bounds = coordinates.boundsInWindow()
                    fieldBoundsInWindow = IntRect(
                        left = bounds.left.roundToInt(),
                        top = bounds.top.roundToInt(),
                        right = bounds.right.roundToInt(),
                        bottom = bounds.bottom.roundToInt()
                    )
                }
                .heightIn(min = 58.dp)
                .appLiquidGlassSurface(
                    shape = AppComponentTokens.ControlShape,
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                    level = LiquidGlassSurfaceLevel.Control,
                    backdropSamplingEnabled = false
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = !expanded
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Text(
            text = label,
            modifier = Modifier.weight(labelWeight),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = selectedLabel,
            modifier = Modifier.weight(valueWeight),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else 0.38f
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = valueTextAlign,
            maxLines = 2
        )
        val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (enabled) 1f else 0.38f
        )
        Canvas(
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = arrowRotation }
        ) {
            drawLine(
                color = arrowColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.38f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.68f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = arrowColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.68f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.38f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        }
        if ((popupVisibility.currentState || popupVisibility.targetState) && anchorWidthPx > 0) {
            Popup(
            onDismissRequest = { expanded = false },
            popupPositionProvider = popupPositionProvider,
            properties = PopupProperties(focusable = true)
            ) {
            val selectedColor = MaterialTheme.colorScheme.primary
            AnimatedVisibility(
                visibleState = popupVisibility,
                enter = fadeIn(tween(durationMillis = 120)) + scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(durationMillis = 160)
                ),
                exit = fadeOut(tween(durationMillis = 90)) + scaleOut(
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(durationMillis = 110)
                )
            ) {
                Column(
                    modifier = Modifier
                        .width(popupWidth)
                        .heightIn(max = 360.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = popupShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f)
                        )
                        .appLiquidGlassSurface(
                            shape = popupShape,
                            fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            level = LiquidGlassSurfaceLevel.Floating,
                            backdrop = popupBackdrop,
                            backdropSamplingEnabled = true,
                            blurRadiusMultiplier = 1.6f,
                            tintAlphaMultiplier = 1.5f
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option.value == selected
                        val interactionSource = remember(option.value) { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Button
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelected(option.value)
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Canvas(modifier = Modifier.size(18.dp)) {
                                        drawLine(
                                            color = selectedColor,
                                            start = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.12f,
                                                size.height * 0.55f
                                            ),
                                            end = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.4f,
                                                size.height * 0.8f
                                            ),
                                            strokeWidth = 2.2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                        drawLine(
                                            color = selectedColor,
                                            start = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.4f,
                                                size.height * 0.8f
                                            ),
                                            end = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.9f,
                                                size.height * 0.2f
                                            ),
                                            strokeWidth = 2.2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            Text(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun LiquidGlassDropdownIndicator(
    expanded: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "liquid settings dropdown arrow"
    )
    Canvas(
        modifier = modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.38f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.68f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.68f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.38f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun <T> LiquidGlassDropdownPopup(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect,
    popupWidth: Dp,
    selected: T?,
    options: List<AppSelectOption<T>>,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    val density = LocalDensity.current
    val popupGapPx = with(density) { 6.dp.roundToPx() }
    val popupBackdrop = LocalLiquidGlassContentBackdrop.current
    val popupShape = SmoothRoundedCornerShape(20.dp)
    val haptic = LocalHapticFeedback.current
    val positionProvider = remember(popupGapPx, anchorBoundsInWindow) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val fieldBounds = anchorBoundsInWindow.takeIf { it.width > 0 && it.height > 0 }
                    ?: anchorBounds
                val preferredX = fieldBounds.right - popupContentSize.width
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val x = preferredX.coerceIn(0, maxX)
                val below = fieldBounds.bottom + popupGapPx
                val above = fieldBounds.top - popupContentSize.height - popupGapPx
                val y = if (below + popupContentSize.height <= windowSize.height) {
                    below
                } else {
                    above.coerceAtLeast(0)
                }
                return IntOffset(x, y)
            }
        }
    }
    LaunchedEffect(expanded) {
        visibility.targetState = expanded
    }
    if (
        (visibility.currentState || visibility.targetState) &&
        anchorBoundsInWindow.width > 0 &&
        popupWidth > 0.dp
    ) {
        Popup(
            onDismissRequest = onDismiss,
            popupPositionProvider = positionProvider,
            properties = PopupProperties(focusable = true)
        ) {
            val selectedColor = MaterialTheme.colorScheme.primary
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(durationMillis = 120)) + scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(durationMillis = 160)
                ),
                exit = fadeOut(tween(durationMillis = 90)) + scaleOut(
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(durationMillis = 110)
                )
            ) {
                Column(
                    modifier = Modifier
                        .width(popupWidth)
                        .heightIn(max = 360.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = popupShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f)
                        )
                        .appLiquidGlassSurface(
                            shape = popupShape,
                            fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            level = LiquidGlassSurfaceLevel.Floating,
                            backdrop = popupBackdrop,
                            backdropSamplingEnabled = true,
                            blurRadiusMultiplier = 1.6f,
                            tintAlphaMultiplier = 1.5f
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option.value == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    interactionSource = remember(option.value) {
                                        MutableInteractionSource()
                                    },
                                    indication = null,
                                    role = Role.Button
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelected(option.value)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Canvas(modifier = Modifier.size(18.dp)) {
                                        drawLine(
                                            color = selectedColor,
                                            start = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.12f,
                                                size.height * 0.55f
                                            ),
                                            end = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.4f,
                                                size.height * 0.8f
                                            ),
                                            strokeWidth = 2.2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                        drawLine(
                                            color = selectedColor,
                                            start = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.4f,
                                                size.height * 0.8f
                                            ),
                                            end = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.9f,
                                                size.height * 0.2f
                                            ),
                                            strokeWidth = 2.2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            Text(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val containerColor = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.secondaryVariant
        }
        val labelColor = if (selected) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.onSecondaryVariant
        }
        MiuixSurface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = AppComponentTokens.ChipHeight),
            color = containerColor,
            shape = SmoothRoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(LocalContentColor provides labelColor, content = label)
            }
        }
        return
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = AppComponentTokens.ChipHeight),
        enabled = enabled,
        shape = AppComponentTokens.ControlShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerHigh,
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.secondaryContainer,
            selectedLabelColor = colors.onSecondaryContainer,
            disabledContainerColor = colors.surfaceContainer,
            disabledLabelColor = colors.onSurface.copy(alpha = 0.38f)
        )
    )
}

@Composable
fun AppDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = AppComponentTokens.DialogMaxWidth)
                    .appLiquidGlassSurface(
                        shape = AppComponentTokens.DialogShape,
                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        level = LiquidGlassSurfaceLevel.Floating,
                        backdropSamplingEnabled = false
                    ),
                content = content
            )
        }
    }
}

/**
 * Theme-native modal sheet host.
 *
 * Miuix delegates to the library's SuperBottomSheet, Material keeps the M3
 * implementation, and LiquidGlass owns its scrim, surface, and motion instead
 * of wrapping a transparent Material sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    when (LocalAppUiTheme.current) {
        AppUiTheme.MIUIX -> {
            val show = remember { mutableStateOf(true) }
            MiuixSuperBottomSheet(
                show = show,
                modifier = modifier,
                title = title,
                onDismissRequest = onDismissRequest,
                content = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        content = content
                    )
                }
            )
        }

        AppUiTheme.MATERIAL -> {
            MaterialModalBottomSheet(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }
        }

        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> {
            val visibility = remember {
                MutableTransitionState(false).apply { targetState = true }
            }
            val scope = rememberCoroutineScope()
            var dismissing by remember { mutableStateOf(false) }
            val requestDismiss = {
                if (!dismissing) {
                    dismissing = true
                    visibility.targetState = false
                    scope.launch {
                        delay(180)
                        onDismissRequest()
                    }
                }
            }

            Dialog(
                onDismissRequest = requestDismiss,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = requestDismiss
                        )
                ) {
                    AnimatedVisibility(
                        visibleState = visibility,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        enter = fadeIn(tween(150)) +
                            slideInVertically(tween(220)) { height -> height / 5 },
                        exit = fadeOut(tween(120)) +
                            slideOutVertically(tween(180)) { height -> height / 5 }
                    ) {
                        val sheetShape = SmoothRoundedCornerShape(32.dp)
                        Column(
                            modifier = modifier
                                .fillMaxWidth()
                                .heightIn(max = 720.dp)
                                .navigationBarsPadding()
                                .shadow(18.dp, sheetShape, clip = false)
                                .clip(sheetShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    0.75.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    sheetShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, top = 20.dp, end = 18.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(SmoothRoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = requestDismiss
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val closeIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    Canvas(modifier = Modifier.size(16.dp)) {
                                        val stroke = 2.dp.toPx()
                                        drawLine(
                                            color = closeIconColor,
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                            strokeWidth = stroke,
                                            cap = StrokeCap.Round
                                        )
                                        drawLine(
                                            color = closeIconColor,
                                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                            strokeWidth = stroke,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            content()
                        }
                    }
                }
            }
        }
    }
}
