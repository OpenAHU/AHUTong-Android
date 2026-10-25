package com.ahu.ahutong.ui.screen.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.screen.main.weather.*
import com.ahu.ahutong.ui.state.WeatherHomeMode
import com.ahu.ahutong.ui.state.WeatherViewModel

private enum class WeatherDetailPage(val title: String) {
    HOME(""), AIR("空气质量"), FORECAST("天气预报"), DAILY("15日天气")
}

@Composable
fun Weather(
    weatherViewModel: WeatherViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        WeatherContent(weatherViewModel, onBack)
    } else {
        BreezyWeatherTheme { WeatherContent(weatherViewModel, onBack) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherContent(
    weatherViewModel: WeatherViewModel,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val weather = weatherViewModel.weather
    val breezy = LocalAppUiTheme.current != AppUiTheme.MIUIX
    val palette = if (breezy) breezyWeatherPalette(weather) else weatherPalette(weather)
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val collapseOffset = with(LocalDensity.current) { 160.dp.roundToPx() }
    var requestedWeather by rememberSaveable { mutableStateOf(false) }
    var searchCity by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var detailPage by rememberSaveable { mutableStateOf(WeatherDetailPage.HOME) }
    val detailListState = remember(detailPage) { LazyListState() }
    val backAction: (() -> Unit)? = if (detailPage != WeatherDetailPage.HOME) {
        { detailPage = WeatherDetailPage.HOME }
    } else onBack
    val collapsed by remember(listState, collapseOffset) { derivedStateOf {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > collapseOffset
    } }

    WeatherSystemBars(palette.top.luminance() > .5f, palette.bottom.luminance() > .5f)

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        weatherViewModel.fetchWeatherByLocation(context)
    }
    LaunchedEffect(Unit) {
        if (weather == null && !weatherViewModel.isLoading && !requestedWeather) {
            requestedWeather = true
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    val submitCitySearch = {
        searchCity.trim().takeIf { it.isNotEmpty() && !weatherViewModel.isLoading }?.let {
            weatherViewModel.fetchWeather(it)
            showSearch = false
            keyboard?.hide()
        }
    }
    BackHandler(enabled = detailPage != WeatherDetailPage.HOME) { detailPage = WeatherDetailPage.HOME }
    BackHandler(enabled = showSearch) { showSearch = false }
    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    CompositionLocalProvider(LocalContentColor provides palette.content) {
        Box(Modifier.fillMaxSize()) {
            if (breezy) BreezyWeatherBackdrop(weather, palette) else WeatherBackdrop(weather, palette)
            key(detailPage) {
                LazyColumn(
                    state = if (detailPage == WeatherDetailPage.HOME) listState else detailListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (breezy) 16.dp else 8.dp)
                ) {
                    item {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        Spacer(Modifier.height(56.dp))
                        if (detailPage != WeatherDetailPage.HOME) {
                            Text(weatherViewModel.locationName, Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                                fontSize = 15.sp, color = palette.content.copy(alpha = .7f))
                        } else if (weather != null) {
                            if (breezy) {
                                BreezyWeatherHero(weather, palette)
                            } else {
                                WeatherHero(weather, weatherViewModel.locationName) { detailPage = WeatherDetailPage.AIR }
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().heightIn(min = 350.dp).padding(32.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("天气", fontSize = 30.sp, fontWeight = FontWeight.Light)
                                Spacer(Modifier.height(20.dp))
                                if (weatherViewModel.isLoading) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = palette.content)
                                    Spacer(Modifier.height(16.dp))
                                    Text("正在获取天气…", color = palette.content.copy(alpha = .7f))
                                }
                            }
                        }
                    }
                    weatherViewModel.errorMessage?.let { error ->
                        item {
                            WeatherPanel(palette) {
                                Text(if (weather == null) "天气暂时无法获取" else "天气未能更新", fontSize = 17.sp, color = palette.panelContent)
                                Text(error, color = palette.muted, fontSize = 13.sp)
                                TextButton(onClick = weatherViewModel::refresh) { Text("重试", color = palette.panelContent) }
                            }
                        }
                    }
                    if (weather != null) {
                        when (detailPage) {
                            WeatherDetailPage.HOME -> {
                                if (breezy) {
                                    if (!weather.alerts.isNullOrEmpty()) item { BreezyWeatherAlertsPanel(weather.alerts, palette) }
                                } else {
                                    weather.alerts.orEmpty().forEach { alert -> item { WeatherAlertPanel(alert, palette) } }
                                }
                                if (!weather.forecast.isNullOrEmpty()) {
                                    item {
                                        if (breezy) BreezyWeatherForecastPanel(weather, palette, onMoreClick = { detailPage = WeatherDetailPage.DAILY })
                                        else WeatherForecastPanel(weather, palette, onMoreClick = { detailPage = WeatherDetailPage.DAILY })
                                    }
                                }
                                if (!weather.hourlyForecast.isNullOrEmpty()) {
                                    item {
                                        if (breezy) BreezyWeatherHourlyPanel(weather, palette, onDetailsClick = { detailPage = WeatherDetailPage.FORECAST })
                                        else WeatherHourlyPanel(weather, palette, onDetailsClick = { detailPage = WeatherDetailPage.FORECAST })
                                    }
                                }
                                if (!breezy) item { WeatherRainPanel(weather, palette) { detailPage = WeatherDetailPage.FORECAST } }
                                item {
                                    if (breezy) BreezyWeatherDetailsGrid(weather, palette, onAirClick = { detailPage = WeatherDetailPage.AIR })
                                    else WeatherDetailsGrid(weather, palette)
                                }
                                if (!breezy) weather.aqi?.let {
                                    item { WeatherAirPanel(weather, palette, onDetailsClick = { detailPage = WeatherDetailPage.AIR }) }
                                }
                                weather.lifeIndices?.let { item { WeatherLifePanel(it, palette) } }
                            }
                            WeatherDetailPage.AIR -> item { WeatherAirPanel(weather, palette, detailed = true) }
                            WeatherDetailPage.FORECAST -> {
                                item { WeatherRainPanel(weather, palette) }
                                if (!weather.hourlyForecast.isNullOrEmpty()) {
                                    item {
                                        if (breezy) BreezyWeatherHourlyPanel(weather, palette, detailed = true)
                                        else WeatherHourlyPanel(weather, palette, detailed = true)
                                    }
                                }
                            }
                            WeatherDetailPage.DAILY -> item {
                                if (breezy) BreezyWeatherForecastPanel(weather, palette, fullForecast = true)
                                else WeatherForecastPanel(weather, palette, fullForecast = true)
                            }
                        }
                        if (!breezy) weather.reportTime?.takeIf { it.isNotBlank() }?.let { time ->
                            item {
                                Text("更新于 ${time.replace('T', ' ').take(16)}",
                                    Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
                                    fontSize = 11.sp, color = palette.content.copy(alpha = .7f))
                            }
                        }
                    }
                    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(if (collapsed || showSearch || detailPage != WeatherDetailPage.HOME) palette.top.copy(alpha = .96f) else Color.Transparent)
                    .statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                backAction?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, if (detailPage == WeatherDetailPage.HOME) "返回" else "返回天气", tint = palette.content)
                    }
                }
                if (detailPage != WeatherDetailPage.HOME) {
                    Text(detailPage.title, Modifier.weight(1f), fontSize = 20.sp, color = palette.content)
                } else if (showSearch) {
                    TextField(
                        value = searchCity,
                        onValueChange = { searchCity = it },
                        placeholder = { Text("输入城市名，如 合肥", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = palette.content,
                            unfocusedTextColor = palette.content,
                            focusedPlaceholderColor = palette.content.copy(alpha = .55f),
                            unfocusedPlaceholderColor = palette.content.copy(alpha = .55f),
                            cursorColor = palette.content,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitCitySearch() })
                    )
                    IconButton(onClick = { submitCitySearch() }) { Icon(Icons.Default.Search, "搜索", tint = palette.content) }
                    IconButton(onClick = { showSearch = false }) { Icon(Icons.Default.Close, "关闭搜索", tint = palette.content) }
                } else {
                    Text(
                        if (breezy || collapsed) weatherViewModel.locationName.ifBlank { "天气" } else "",
                        Modifier.weight(1f), fontSize = if (breezy) 20.sp else 18.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = palette.content
                    )
                    if (breezy) {
                        if (weatherViewModel.isLoading) {
                            CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(20.dp), color = palette.content, strokeWidth = 2.dp)
                        }
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "搜索城市", tint = palette.content) }
                        Box {
                            IconButton(onClick = { showActions = true }) { Icon(Icons.Default.MoreVert, "更多选项", tint = palette.content) }
                            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                DropdownMenuItem(
                                    text = { Text("定位天气") },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                                    enabled = !weatherViewModel.isLoading,
                                    onClick = {
                                        showActions = false
                                        locationPermissionLauncher.launch(arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("刷新天气") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    enabled = !weatherViewModel.isLoading,
                                    onClick = { showActions = false; weatherViewModel.refresh() }
                                )
                                DropdownMenuItem(
                                    text = { Text("天气设置") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { showActions = false; showSettings = true }
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                locationPermissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            },
                            enabled = !weatherViewModel.isLoading
                        ) {
                            Icon(Icons.Default.LocationOn, "定位天气",
                                tint = palette.content.copy(alpha = if (weatherViewModel.isLoading) .4f else 1f))
                        }
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "搜索城市", tint = palette.content) }
                        IconButton(onClick = weatherViewModel::refresh, enabled = !weatherViewModel.isLoading) {
                            if (weatherViewModel.isLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = palette.content, strokeWidth = 2.dp)
                            } else Icon(Icons.Default.Refresh, "刷新天气", tint = palette.content)
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "天气设置", tint = palette.content) }
                    }
                }
            }
        }
    }

    if (showSettings) {
        val config = weatherViewModel.homeConfig
        AppModalBottomSheet(title = "天气设置", onDismissRequest = { showSettings = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("首页天气样式", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(WeatherHomeMode.Detailed to "详细", WeatherHomeMode.Compact to "精简").forEach { (mode, label) ->
                        AppFilterChip(
                            selected = config.mode == mode,
                            onClick = { weatherViewModel.updateHomeConfig(config.copy(mode = mode)) },
                            label = { Text(label) }
                        )
                    }
                }
                val options = listOf(
                    Triple("在首页显示天气", config.showOnHome) { value: Boolean -> config.copy(showOnHome = value) },
                    Triple("详细卡片显示城市名", config.showLocation) { value: Boolean -> config.copy(showLocation = value) },
                    Triple("详细卡片显示温度", config.showTemp) { value: Boolean -> config.copy(showTemp = value) },
                    Triple("详细卡片显示天气状况", config.showWeather) { value: Boolean -> config.copy(showWeather = value) },
                    Triple("详细卡片显示空气质量", config.showAqi) { value: Boolean -> config.copy(showAqi = value) }
                )
                options.forEach { (label, checked, update) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        AppToggle(checked, { weatherViewModel.updateHomeConfig(update(it)) }, contentDescription = label)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WeatherSystemBars(lightStatus: Boolean, lightNavigation: Boolean) {
    val view = LocalView.current
    val window = remember(view) {
        var current: Context? = view.context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        (current as? Activity)?.window
    }
    DisposableEffect(window, view, lightStatus, lightNavigation) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = lightStatus
        controller?.isAppearanceLightNavigationBars = lightNavigation
        onDispose {
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
    SideEffect {
        window?.let { WindowCompat.getInsetsController(it, view) }?.apply {
            isAppearanceLightStatusBars = lightStatus
            isAppearanceLightNavigationBars = lightNavigation
        }
    }
}
