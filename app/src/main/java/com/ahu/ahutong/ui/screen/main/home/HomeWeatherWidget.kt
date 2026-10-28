package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.ui.components.AppCircularProgressIndicator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ahu.ahutong.data.weather.WeatherApi
import com.ahu.ahutong.data.weather.WeatherResponse
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.WeatherHomeConfig
import com.ahu.ahutong.ui.state.WeatherHomeMode
import com.kyant.monet.a1
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.painterResource
import com.ahu.ahutong.R
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun rememberHomeWeatherConfig(accountId: String?, isActive: Boolean): WeatherHomeConfig =
    remember(accountId, isActive) { WeatherHomeConfig.fromCache() }

@Composable
fun HomeWeatherWidget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    config: WeatherHomeConfig = WeatherHomeConfig.fromCache(),
    mode: WeatherHomeMode = config.mode
) {
    if (!config.showOnHome) return

    val context = androidx.compose.ui.platform.LocalContext.current
    var weather by remember { mutableStateOf<WeatherResponse?>(null) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        hasError = false
        runCatching {
            withContext(Dispatchers.IO) {
                val city = getCityFromLocation(context)
                WeatherApi.API.getWeather(city = city)
            }
        }.onSuccess {
            weather = it
        }.onFailure {
            hasError = true
        }
    }

    when (mode) {
        WeatherHomeMode.Compact -> CompactHomeWeatherCard(
            modifier = modifier,
            weather = weather,
            hasError = hasError,
            onClick = onClick
        )

        WeatherHomeMode.Detailed -> DetailedHomeWeatherCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            weather = weather,
            hasError = hasError,
            config = config,
            onClick = onClick
        )
    }
}

@Composable
private fun DetailedHomeWeatherCard(
    modifier: Modifier,
    weather: WeatherResponse?,
    hasError: Boolean,
    config: WeatherHomeConfig,
    onClick: () -> Unit
) {
    val shape = SmoothRoundedCornerShape(32.dp)
    Card(
        modifier = modifier
            .appLiquidGlassSurface(
                shape = shape,
                fallbackColor = 100.n1 withNight 20.n1
            )
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val w = weather
            when {
                w != null && !hasError -> DetailedHomeWeatherContent(w, config)
                hasError -> {
                    Text(
                        text = "天气暂不可用",
                        textAlign = TextAlign.Center,
                        color = 50.n1 withNight 80.n1
                    )
                }
                else -> {
                    AppCircularProgressIndicator(
                        size = 24.dp,
                        strokeWidth = 2.dp,
                        color = 70.a1 withNight 85.a1
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedHomeWeatherContent(weather: WeatherResponse, config: WeatherHomeConfig) {
    val rainKeywords = listOf("雨", "雪", "雹")
    val containsRain = { s: String? -> s != null && rainKeywords.any { s.contains(it) } }
    val rainPossible = containsRain(weather.weather) ||
        weather.forecast?.firstOrNull()
            ?.let { containsRain(it.weatherDay) || containsRain(it.weatherNight) } == true ||
        weather.hourlyForecast?.take(6)?.any { containsRain(it.weather) } == true

    val isSunny = weather.weather?.let { it.contains("晴") && !it.contains("多云") } == true
    val highUv = (weather.uv ?: 0.0) >= 5.0

    val windArrow = when (weather.windDirection) {
        "北风" -> "\u2193"
        "南风" -> "\u2191"
        "东风" -> "\u2190"
        "西风" -> "\u2192"
        "东北风" -> "\u2199"
        "西北风" -> "\u2198"
        "东南风" -> "\u2196"
        "西南风" -> "\u2197"
        else -> when {
            (weather.windDirection ?: "").contains("北") &&
                (weather.windDirection ?: "").contains("东") -> "\u2199"
            (weather.windDirection ?: "").contains("北") &&
                (weather.windDirection ?: "").contains("西") -> "\u2198"
            (weather.windDirection ?: "").contains("南") &&
                (weather.windDirection ?: "").contains("东") -> "\u2196"
            (weather.windDirection ?: "").contains("南") &&
                (weather.windDirection ?: "").contains("西") -> "\u2197"
            (weather.windDirection ?: "").contains("北") -> "\u2193"
            (weather.windDirection ?: "").contains("南") -> "\u2191"
            (weather.windDirection ?: "").contains("东") -> "\u2190"
            (weather.windDirection ?: "").contains("西") -> "\u2192"
            else -> "\u2198"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (config.showLocation) {
                Text(
                    text = listOfNotNull(weather.district, weather.city).firstOrNull() ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = 50.n1 withNight 80.n1
                )
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (config.showTemp) {
                    Text(
                        text = "${weather.temperature?.toInt() ?: "--"}°",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        color = 0.n1 withNight 100.n1
                    )
                }
                if (config.showTemp && config.showWeather) {
                    Spacer(Modifier.width(8.dp))
                }
                if (config.showWeather) {
                    Text(
                        text = weather.weather ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = 0.n1 withNight 100.n1
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            if (weather.windDirection != null || weather.windPower != null) {
                Text(
                    text = "$windArrow ${weather.windDirection ?: ""} ${weather.windPower ?: ""}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = 50.n1 withNight 80.n1
                )
            }
            if (config.showAqi && weather.aqi != null) {
                val aqiEmoji = when (weather.aqiLevel) {
                    1 -> "\uD83D\uDFE2"
                    2 -> "\uD83D\uDFE1"
                    3 -> "\uD83D\uDFE0"
                    4 -> "\uD83D\uDD34"
                    5 -> "\uD83D\uDFE3"
                    else -> "\u26AA"
                }
                Text(
                    text = "$aqiEmoji 空气${weather.aqi} ${weather.aqiCategory ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = 50.n1 withNight 80.n1
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            if (rainPossible) {
                val pops = weather.hourlyForecast?.take(6)?.mapNotNull { it.pop } ?: emptyList()
                val maxPop = if (pops.isNotEmpty()) pops.max() else 0
                val label = when {
                    maxPop >= 60 -> "务必带伞"
                    maxPop >= 40 -> "建议带伞"
                    maxPop > 0 -> "可能降雨"
                    else -> "带伞"
                }
                Text("\u2614", fontSize = 24.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (maxPop > 0) "${maxPop}%" else "--",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = 80.a1 withNight 90.a1
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = 50.n1 withNight 80.n1
                )
            } else if (isSunny && highUv) {
                Text("\u2600\uFE0F", fontSize = 24.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "UV${weather.uv?.toInt() ?: "--"}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = 80.a1 withNight 90.a1
                )
                Text(
                    text = "防晒",
                    fontSize = 12.sp,
                    color = 50.n1 withNight 80.n1
                )
            }
        }
    }
}

@Composable
private fun CompactHomeWeatherCard(
    modifier: Modifier,
    weather: WeatherResponse?,
    hasError: Boolean,
    onClick: () -> Unit
) {
    // 标题栏胶囊形态：图标 + 温度 + 雨情。无雨主题色、有雨黄色。
    val rainKeywords = listOf("雨", "雷", "雪", "冰雹")
    val hasRain = weather?.let { w ->
        rainKeywords.any { (w.weather ?: "").contains(it) }
    } == true
    val contentColor = when {
        weather == null || hasError -> 55.n1 withNight 70.n1
        hasRain -> Color(0xFFF5C542) // 有雨：暖黄
        else -> MaterialTheme.colorScheme.primary // 无雨：主题色
    }
    Row(
        modifier = modifier
            .appLiquidGlassSurface(
                shape = ContinuousCapsule,
                fallbackColor = 100.n1 withNight 20.n1
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(
                if (hasRain) R.drawable.ic_home_umbrella else R.drawable.ic_home_weather
            ),
            contentDescription = if (hasRain) "有雨" else "天气",
            modifier = Modifier.size(15.dp),
            tint = contentColor
        )
        Text(
            text = when {
                weather != null && !hasError ->
                    "${weather.temperature?.toInt() ?: "--"}° ${if (hasRain) "雨" else "无雨"}"
                hasError -> "--"
                else -> "…"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun CompactHomeWeatherContent(weather: WeatherResponse) {
    val symbol = weather.homeWeatherSymbol()
    val metricLines = weather.compactMetricLines()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = symbol.glyph,
            fontSize = 19.sp,
            color = 70.a1 withNight 85.a1
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${weather.temperature?.toInt() ?: "--"}°",
                fontSize = 17.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = 0.n1 withNight 100.n1,
                maxLines = 1
            )
            Text(
                text = weather.weather ?: symbol.description,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = 45.n1 withNight 78.n1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            modifier = Modifier.widthIn(min = 42.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            metricLines.forEach { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = 55.n1 withNight 82.n1,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CompactHomeWeatherUnavailable() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "\u2022",
            fontSize = 20.sp,
            color = 50.n1 withNight 80.n1
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "--°",
                fontSize = 17.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = 0.n1 withNight 100.n1
            )
            Text(
                text = "天气",
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = 45.n1 withNight 78.n1
            )
        }
    }
}

private data class HomeWeatherSymbol(
    val glyph: String,
    val description: String
)

private fun WeatherResponse.compactMetricLines(): List<String> {
    val nextSixHours = hourlyForecast.orEmpty().take(6)
    val maxPop = nextSixHours.mapNotNull { it.pop }.maxOrNull()
    val rainKeywords = listOf("雨", "雪", "雹")
    val containsRain = { text: String? -> text != null && rainKeywords.any { text.contains(it) } }
    val hasRainHint = containsRain(weather) ||
        forecast?.firstOrNull()?.let {
            containsRain(it.weatherDay) || containsRain(it.weatherNight)
        } == true ||
        nextSixHours.any { containsRain(it.weather) }

    val rainLine = when {
        maxPop != null -> "雨 ${maxPop}%"
        hasRainHint -> "雨 --"
        else -> null
    }
    val uvIndex = uv?.toInt()
        ?: hourlyForecast?.firstOrNull()?.uvIndex
        ?: forecast?.firstOrNull()?.uvIndex
    val uvLine = uvIndex?.let { "UV $it" }
    val fallbackLine = humidity?.let { "湿 $it%" } ?: windPower?.let { "风 $it" }

    return listOfNotNull(rainLine, uvLine, fallbackLine).take(2)
}

private fun WeatherResponse.homeWeatherSymbol(): HomeWeatherSymbol {
    val code = (weatherCode ?: weatherIcon)?.toIntOrNull()
    val text = weather.orEmpty()
    return when {
        code.isOneOf(100, 150) || text.hasAnyWeatherKeyword("晴") ->
            HomeWeatherSymbol("\u2600", "晴")
        code.isIn(101..103) || code.isIn(151..153) || text.hasAnyWeatherKeyword("多云", "少云", "云") ->
            HomeWeatherSymbol("\u26C5", "多云")
        code.isOneOf(104, 154) || text.hasAnyWeatherKeyword("阴") ->
            HomeWeatherSymbol("\u2601", "阴")
        code.isIn(300..399) || text.hasAnyWeatherKeyword("雨", "雹") ->
            HomeWeatherSymbol("\u2614", "降水")
        code.isIn(400..499) || text.hasAnyWeatherKeyword("雪") ->
            HomeWeatherSymbol("\u2744", "降雪")
        code.isIn(500..515) || text.hasAnyWeatherKeyword("雾", "霾", "沙", "尘") ->
            HomeWeatherSymbol("\u224B", "雾霾")
        code.isIn(200..213) || text.hasAnyWeatherKeyword("风") ->
            HomeWeatherSymbol("\u219D", "风")
        code.isOneOf(900, 901) ->
            HomeWeatherSymbol("\u00B0", "气温")
        else ->
            HomeWeatherSymbol("\u2601", "天气")
    }
}

private fun Int?.isIn(range: IntRange): Boolean {
    return this != null && this in range
}

private fun Int?.isOneOf(vararg values: Int): Boolean {
    return this != null && values.contains(this)
}

private fun String.hasAnyWeatherKeyword(vararg keywords: String): Boolean {
    return keywords.any { contains(it) }
}

internal fun getCityFromLocation(context: Context): String? {
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarseLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasFineLocation && !hasCoarseLocation) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    val location = runCatching {
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }.getOrNull() ?: runCatching {
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }.getOrNull() ?: return null

    val geocoder = Geocoder(context, Locale.CHINA)
    val addresses = runCatching {
        geocoder.getFromLocation(location.latitude, location.longitude, 1)
    }.getOrNull() ?: return null

    val address = addresses.firstOrNull() ?: return null
    return address.locality ?: address.subAdminArea
}
