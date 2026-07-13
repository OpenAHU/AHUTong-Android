package com.ahu.ahutong.ui.screen.main

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.weather.WeatherResponse
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuIconActionGroup
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.WeatherViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Weather(
    weatherViewModel: WeatherViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val weather = weatherViewModel.weather
    val hasSearched = rememberSaveable { mutableStateOf(false) }

    var searchCity by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            weatherViewModel.fetchWeatherByLocation(context)
        } else {
            weatherViewModel.fetchWeather()
        }
    }

    LaunchedEffect(Unit) {
        if (weather == null && !hasSearched.value) {
            hasSearched.value = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                weatherViewModel.fetchWeatherByLocation(context)
            }
        }
    }

    AhuScreen(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPaddingBottom = AhuDimens.ContentHorizontal,
    ) {
        if (showSearch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AhuDimens.ContentHorizontal, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    showSearch = false
                    searchCity = ""
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭搜索")
                }
                val doSearch = {
                    if (searchCity.isNotBlank()) {
                        weatherViewModel.fetchWeather(searchCity)
                        showSearch = false
                    }
                }
                OutlinedTextField(
                    value = searchCity,
                    onValueChange = { searchCity = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入城市名，如 合肥") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AhuColors.onSurface,
                        unfocusedTextColor = AhuColors.onSurface,
                        cursorColor = AhuColors.primaryAction,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    trailingIcon = {
                        if (searchCity.isNotEmpty()) {
                            IconButton(onClick = { searchCity = "" }) {
                                Icon(Icons.Default.Close, "清空")
                            }
                        } else {
                            IconButton(onClick = { doSearch() }) {
                                Icon(Icons.Default.Search, "搜索")
                            }
                        }
                    }
                )
            }
        } else {
            AhuPageHeader(
                title = weatherViewModel.locationName.ifBlank { "天气" },
                titleStyle = MaterialTheme.typography.headlineSmall,
                actions = {
                    AhuIconActionGroup {
                        AhuHeaderIconButton(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索城市",
                            onClick = { showSearch = true },
                        )
                        AhuHeaderIconButton(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            onClick = { showSettings = true },
                        )
                        AhuHeaderIconButton(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            onClick = {
                                weatherViewModel.refresh()
                                Toast.makeText(context, "已刷新", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                },
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = AhuDimens.ContentHorizontal),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (weatherViewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AhuColors.primaryAction)
                }
            } else if (weatherViewModel.errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        weatherViewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    AhuPrimaryButton(
                        text = "重试",
                        onClick = { weatherViewModel.refresh() },
                    )
                }
            } else if (weather != null) {
                WeatherCard(weather)

                weather.forecast?.let { forecast ->
                    Text(
                        "未来预报",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AhuColors.onSurface,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(forecast) { day -> ForecastCard(day) }
                    }
                }

                weather.aqi?.let {
                    AqiCard(weather)
                }

                UmbrellaCard(weather)

                weather.hourlyForecast?.let { hourly ->
                    if (hourly.isNotEmpty()) {
                        Text(
                            "逐小时预报",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AhuColors.onSurface,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hourly.take(12)) { h -> HourlyCard(h) }
                        }
                    }
                }

                weather.lifeIndices?.let { indices ->
                    Text(
                        "生活指数",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AhuColors.onSurface,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    LifeIndicesGrid(indices)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showSettings) {
        val config = weatherViewModel.homeConfig
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = AhuColors.pageBackground,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "天气设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AhuColors.onSurface
                )
                Text(
                    "选择要在首页卡片上显示的信息：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AhuColors.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))

                data class SettingItem(val key: String, val label: String, val value: Boolean, val onChange: (Boolean) -> Unit)

                val settings = listOf(
                    SettingItem("showOnHome", "在首页显示天气", config.showOnHome) {
                        weatherViewModel.updateHomeConfig(config.copy(showOnHome = it))
                    },
                    SettingItem("showLocation", "显示城市名", config.showLocation) {
                        weatherViewModel.updateHomeConfig(config.copy(showLocation = it))
                    },
                    SettingItem("showTemp", "显示温度", config.showTemp) {
                        weatherViewModel.updateHomeConfig(config.copy(showTemp = it))
                    },
                    SettingItem("showWeather", "显示天气状况", config.showWeather) {
                        weatherViewModel.updateHomeConfig(config.copy(showWeather = it))
                    },
                    SettingItem("showAqi", "显示空气质量", config.showAqi) {
                        weatherViewModel.updateHomeConfig(config.copy(showAqi = it))
                    },
                )

                settings.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.label,
                            modifier = Modifier.weight(1f),
                            color = AhuColors.onSurface
                        )
                        Switch(checked = item.value, onCheckedChange = item.onChange)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherResponse) {
    AhuCard(
        containerColor = AhuColors.primaryAction,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = listOfNotNull(weather.district, weather.city).firstOrNull() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = AhuColors.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${weather.temperature?.toInt() ?: "--"}°",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = AhuColors.onSurface
            )
            Text(
                text = weather.weather ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = AhuColors.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "体感 ${weather.feelsLike?.toInt() ?: "--"}°",
                style = MaterialTheme.typography.bodyMedium,
                color = AhuColors.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem("湿度", "${weather.humidity ?: "--"}%")
                InfoItem("风力", weather.windPower ?: "--")
                InfoItem("能见度", "${weather.visibility?.toInt() ?: "--"}km")
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AhuColors.onSurface.copy(alpha = 0.55f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = AhuColors.onSurface,
        )
    }
}

@Composable
private fun ForecastCard(day: com.ahu.ahutong.data.weather.ForecastDay) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(SmoothRoundedCornerShape(AhuDimens.CardCorner))
            .background(AhuColors.card)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(day.week ?: "", style = MaterialTheme.typography.labelMedium, color = AhuColors.onSurface)
        Text("${day.tempMax?.toInt() ?: "--"}°", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AhuColors.onSurface)
        Text("${day.tempMin?.toInt() ?: "--"}°", color = AhuColors.onSurface.copy(alpha = 0.55f), fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(day.weatherDay ?: "", fontSize = 12.sp, maxLines = 1, color = AhuColors.onSurface)
    }
}

@Composable
private fun AqiCard(weather: WeatherResponse) {
    val categoryColor = when (weather.aqiLevel) {
        1 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        2 -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        3 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        4 -> androidx.compose.ui.graphics.Color(0xFFFF5722)
        5 -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        6 -> androidx.compose.ui.graphics.Color(0xFF880E4F)
        else -> androidx.compose.ui.graphics.Color.Gray
    }
    AhuCard(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${weather.aqi ?: "--"}",
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("空气${weather.aqiCategory ?: ""}", fontWeight = FontWeight.Bold, color = AhuColors.onSurface)
                Text(
                    "主要污染物：${weather.aqiPrimary ?: "无"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AhuColors.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun UmbrellaCard(weather: WeatherResponse) {
    val (needUmbrella, maxPop) = remember(weather) {
        val rainKeywords = listOf("雨", "雪", "雹")
        val containsRain = { s: String? -> s != null && rainKeywords.any { s.contains(it) } }

        var score = 0
        if (containsRain(weather.weather)) score += 3
        val today = weather.forecast?.firstOrNull()
        if (today != null && (containsRain(today.weatherDay) || containsRain(today.weatherNight))) score += 2
        val next6 = weather.hourlyForecast?.take(6)
        val next6Pops = next6?.mapNotNull { it.pop } ?: emptyList()
        if (next6 != null && next6.any { containsRain(it.weather) }) score += 2
        val next12 = weather.hourlyForecast?.take(12)
        if (next12 != null && next12.any { containsRain(it.weather) } && score == 0) score += 1

        val pop = if (next6Pops.isNotEmpty()) next6Pops.max() else 0
        Pair(score >= 3, pop)
    }

    val (title, subtitle) = when {
        !needUmbrella -> Pair("无需雨伞", "当前及短期预报无降水")
        maxPop >= 60 -> Pair("务必带伞 \u2614", "未来6小时降雨概率高达 ${maxPop}%")
        maxPop >= 40 -> Pair("建议带伞 \u2614", "未来6小时降雨概率 ${maxPop}%")
        maxPop > 0 -> Pair("可能降雨 \u2614", "降水概率 ${maxPop}%，建议随身带伞")
        else -> Pair("建议带伞 \u2614", "当前天气或预报有降雨")
    }

    val bgColor = if (needUmbrella) {
        androidx.compose.ui.graphics.Color(0xFF2196F3).copy(alpha = 0.15f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.15f)
    }

    AhuCard(
        containerColor = bgColor,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (needUmbrella) "\u2614" else "\u2600\uFE0F", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AhuColors.onSurface)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AhuColors.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun HourlyCard(h: com.ahu.ahutong.data.weather.HourlyForecast) {
    val timeStr = h.time ?: ""
    // Format: "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DDTHH:MM:SS+ZZZZ"
    val sep = if (timeStr.contains("T")) "T" else " "
    val datePart = timeStr.substringAfter("-").take(5) // "MM-DD"
    val hour = timeStr.substringAfter(sep).take(2)     // "HH"
    val label = if (datePart.length == 5 && hour.length == 2) "${datePart}日${hour}时" else timeStr
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(SmoothRoundedCornerShape(AhuDimens.CardCorner))
            .background(AhuColors.card)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AhuColors.onSurface.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${h.temperature?.toInt() ?: "--"}°",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = AhuColors.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(h.weather ?: "", fontSize = 11.sp, maxLines = 1, color = AhuColors.onSurface)
    }
}

@Composable
private fun LifeIndicesGrid(indices: com.ahu.ahutong.data.weather.LifeIndices) {
    val items = listOf(
        "穿衣" to indices.clothing,
        "紫外线" to indices.uv,
        "洗车" to indices.carWash,
        "运动" to indices.exercise,
        "感冒" to indices.coldRisk,
        "防晒" to indices.sunscreen,
        "舒适度" to indices.comfort,
        "晾晒" to indices.drying,
        "交通" to indices.traffic,
        "过敏" to indices.allergy,
        "钓鱼" to indices.fishing,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.filter { it.second != null }.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, item) ->
                    AhuCard(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AhuColors.onSurface)
                        Text(item!!.level ?: "", color = AhuColors.primaryAction, fontSize = 13.sp)
                        val brief = item.brief
                        if (!brief.isNullOrBlank()) {
                            Text(
                                brief,
                                style = MaterialTheme.typography.bodySmall,
                                color = AhuColors.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}
