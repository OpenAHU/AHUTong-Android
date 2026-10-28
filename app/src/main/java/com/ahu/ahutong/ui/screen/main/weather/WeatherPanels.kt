package com.ahu.ahutong.ui.screen.main.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.data.weather.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Muted = Color.White.copy(alpha = .7f)
private val TemperatureColors = listOf(Color(0xFFBEE8C7), Color(0xFFFFD15B))
private val LocalBreezyWeatherStyle = staticCompositionLocalOf { false }

internal data class WeatherPalette(
    val top: Color,
    val bottom: Color,
    val panel: Color,
    val content: Color = Color.White,
    val panelContent: Color = content,
    val muted: Color = panelContent.copy(alpha = .7f),
    val accent: Color = Color(0xFFFFD36D),
    val breezy: Boolean = false
)

internal fun weatherPalette(weather: WeatherResponse?): WeatherPalette {
    if (weather?.isDaylight != true) {
        return WeatherPalette(Color(0xFF101421), Color(0xFF303A60), Color(0xED191B23))
    }
    return when {
        weather.weather.orEmpty().let { "雨" in it || "雪" in it } ->
            WeatherPalette(Color(0xFF3A5067), Color(0xFF587086), Color(0xD52D4458))
        weather.weather.orEmpty().let { "阴" in it || "雾" in it || "霾" in it || "沙" in it || "尘" in it } ->
            WeatherPalette(Color(0xFF41566F), Color(0xFF61758B), Color(0xD52F425A))
        else -> WeatherPalette(Color(0xFF1E649C), Color(0xFF48799C), Color(0xD51C4568))
    }
}

@Composable
internal fun WeatherBackdrop(weather: WeatherResponse?, palette: WeatherPalette) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
        val clear = weather?.weather.orEmpty().let { "晴" in it && "雨" !in it }
        val daylight = weather?.isDaylight == true
        if (clear && daylight) {
            val center = Offset(size.width * .87f, size.height * .16f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x1EFFF3CB), Color.Transparent), center, size.width * .7f),
                radius = size.width * .7f, center = center
            )
        } else {
            // Static translucent layers replace bitmap skies and continuous weather rendering.
            listOf(Offset(.14f, .15f), Offset(.8f, .28f), Offset(.4f, .43f)).forEachIndexed { index, point ->
                val center = Offset(size.width * point.x, size.height * point.y)
                val radius = size.width * (.42f + index * .08f)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = if (daylight) .025f else .035f), Color.Transparent), center, radius),
                    radius = radius, center = center
                )
            }
        }
        if (clear && !daylight) {
            listOf(Offset(.22f, .12f), Offset(.72f, .17f), Offset(.89f, .37f), Offset(.51f, .28f)).forEach {
                drawCircle(Color.White.copy(alpha = .35f), 1.dp.toPx(), Offset(size.width * it.x, size.height * it.y))
            }
        }
    }
}

@Composable
internal fun WeatherPanel(
    palette: WeatherPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.padding(horizontal = if (palette.breezy) 16.dp else 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(if (palette.breezy) 32.dp else 24.dp)).background(palette.panel).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides palette.panelContent,
            LocalBreezyWeatherStyle provides palette.breezy) { content() }
    }
}

@Composable
internal fun WeatherHero(weather: WeatherResponse, location: String, onAirClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 64.dp, bottom = 60.dp)) {
        Text(location.ifBlank { weather.city.orEmpty() }, fontSize = 21.sp, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Text(
                weather.temperature?.roundToInt()?.toString() ?: "--", fontSize = 132.sp,
                fontWeight = FontWeight.ExtraLight, fontFamily = FontFamily.SansSerif,
                color = Color(0xFFF0F2FF), lineHeight = 142.sp, letterSpacing = (-7).sp
            )
            Text("°", Modifier.padding(top = 17.dp, start = 6.dp), fontSize = 50.sp,
                fontWeight = FontWeight.Light, color = Color(0xFFF0F2FF))
        }
        Spacer(Modifier.height(20.dp))
        val high = weather.tempMax ?: weather.forecast?.firstOrNull()?.tempMax
        val low = weather.tempMin ?: weather.forecast?.firstOrNull()?.tempMin
        Text(
            listOfNotNull(weather.weather?.takeIf { it.isNotBlank() },
                high?.let { "最高${it.roundToInt()}°" }, low?.let { "最低${it.roundToInt()}°" }).joinToString("  "),
            fontSize = 17.sp, color = Color.White.copy(alpha = if (weather.isDaylight == true) 1f else .82f)
        )
        weather.aqi?.let { aqi ->
            Row(
                Modifier.padding(top = 16.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (weather.isDaylight == true) Color.Black.copy(alpha = .15f) else Color.White.copy(alpha = .13f))
                    .clickable(onClickLabel = "查看空气质量", onClick = onAirClick)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Canvas(Modifier.size(15.dp)) {
                    val leaf = Path().apply {
                        moveTo(size.width * .1f, size.height * .8f)
                        cubicTo(0f, 0f, size.width * .8f, 0f, size.width, 0f)
                        cubicTo(size.width, size.height * .8f, size.width * .5f, size.height, size.width * .1f, size.height * .8f)
                        close()
                    }
                    drawPath(leaf, Color.White.copy(alpha = .85f))
                    drawLine(Color(0xFF506074), Offset(size.width * .1f, size.height), Offset(size.width * .7f, size.height * .3f), 1.4.dp.toPx())
                }
                Text("空气${weather.aqiCategory.orEmpty()}  $aqi", fontSize = 14.sp,
                    color = Color.White.copy(alpha = if (weather.isDaylight == true) 1f else .83f))
            }
        }
    }
}

@Composable
internal fun WeatherAlertPanel(alert: WeatherAlert, palette: WeatherPalette) {
    var expanded by rememberSaveable(alert.title, alert.publishTime) { mutableStateOf(false) }
    WeatherPanel(palette, Modifier.clickable { expanded = !expanded }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(alert.title ?: listOfNotNull(alert.type, alert.level).joinToString("") + "预警", fontSize = 17.sp, color = palette.panelContent)
                alert.publishTime?.takeIf { it.isNotBlank() }?.let {
                    Text(it.replace('T', ' ').take(16), fontSize = 12.sp, color = palette.muted)
                }
            }
            Canvas(Modifier.size(30.dp)) {
                val triangle = Path().apply {
                    moveTo(size.width * .5f, size.height * .08f)
                    lineTo(size.width * .95f, size.height * .86f)
                    quadraticTo(size.width, size.height, size.width * .84f, size.height)
                    lineTo(size.width * .16f, size.height)
                    quadraticTo(0f, size.height, size.width * .05f, size.height * .86f)
                    close()
                }
                drawPath(triangle, palette.panelContent.copy(alpha = .6f))
                drawLine(palette.panel, Offset(size.width * .5f, size.height * .38f), Offset(size.width * .5f, size.height * .65f), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(palette.panel, 1.5.dp.toPx(), Offset(size.width * .5f, size.height * .8f))
            }
        }
        alert.text?.let {
            Text(it, color = palette.muted, fontSize = 14.sp, lineHeight = 23.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) alert.guidance.orEmpty().forEach { Text(it, color = palette.muted, fontSize = 13.sp) }
    }
}

@Composable
internal fun WeatherForecastPanel(
    weather: WeatherResponse,
    palette: WeatherPalette,
    fullForecast: Boolean = false,
    onMoreClick: (() -> Unit)? = null
) {
    val days = weather.forecast.orEmpty()
    val min = days.mapNotNull { it.tempMin }.minOrNull() ?: 0.0
    val max = days.mapNotNull { it.tempMax }.maxOrNull() ?: min + 1
    WeatherPanel(palette) {
        days.take(if (fullForecast) 15 else 3).forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(color = palette.panelContent.copy(alpha = .08f))
            Row(Modifier.fillMaxWidth().heightIn(min = 37.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(44.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(when (index) { 0 -> "今天"; 1 -> "明天"; else -> day.week?.replace("星期", "周") ?: day.date?.take(10)?.takeLast(5).orEmpty() },
                        fontSize = 16.sp, color = palette.panelContent, maxLines = 1)
                    if (fullForecast) day.date?.let { Text(it.take(10).takeLast(5), fontSize = 10.sp, color = palette.muted) }
                }
                Text(day.weatherDay.orEmpty(), Modifier.weight(1f), fontSize = 15.sp,
                    color = palette.panelContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                WeatherSymbol(day.weatherDay, true, Modifier.padding(horizontal = 8.dp).size(25.dp))
                Text(weatherDegrees(day.tempMin), Modifier.width(33.dp), fontSize = 14.sp, color = palette.panelContent)
                Canvas(Modifier.padding(horizontal = 5.dp).width(54.dp).height(6.dp)) {
                    val start = (((day.tempMin ?: min) - min) / (max - min).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    val end = (((day.tempMax ?: max) - min) / (max - min).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    drawRoundRect(palette.panelContent.copy(alpha = .12f), cornerRadius = CornerRadius(size.height))
                    if (day.tempMin != null && day.tempMax != null) {
                        drawRoundRect(Brush.horizontalGradient(TemperatureColors),
                            topLeft = Offset(size.width * start, 0f),
                            size = Size((size.width * (end - start)).coerceAtLeast(size.height), size.height),
                            cornerRadius = CornerRadius(size.height))
                    }
                    if (index == 0 && weather.temperature != null && day.tempMin != null && day.tempMax != null) {
                        val current = ((weather.temperature - min) / (max - min).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                        val point = Offset((size.width * current).coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2)
                        drawCircle(palette.panel, size.height * .6f, point)
                        drawCircle(palette.panelContent, size.height * .34f, point)
                    }
                }
                Text(weatherDegrees(day.tempMax), Modifier.width(34.dp), fontSize = 14.sp, color = palette.panelContent,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        if (days.size > 3 && onMoreClick != null) {
            TextButton(
                onClick = onMoreClick,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = palette.panelContent.copy(alpha = .12f))
            ) {
                Text("查看未来${days.size.coerceAtMost(15)}天天气", color = palette.panelContent, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun WeatherHourlyPanel(
    weather: WeatherResponse,
    palette: WeatherPalette,
    detailed: Boolean = false,
    onDetailsClick: (() -> Unit)? = null
) {
    val hours = weather.hourlyForecast.orEmpty().take(24)
    val temperatures = hours.map { it.temperature }
    val low = temperatures.filterNotNull().minOrNull() ?: 0.0
    val high = temperatures.filterNotNull().maxOrNull() ?: low
    var selected by rememberSaveable(weather.reportTime) { mutableIntStateOf(0) }
    WeatherPanel(palette) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("24 小时预报", Modifier.weight(1f), color = palette.muted, fontSize = 14.sp)
            onDetailsClick?.let { action ->
                Text("天气预报 ›", Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = action)
                    .padding(horizontal = 8.dp, vertical = 7.dp), color = palette.panelContent, fontSize = 13.sp)
            }
        }
        val selectedHour = hours.getOrNull(selected)
        Text(
            listOfNotNull(selectedHour?.time?.let(::hourLabel), selectedHour?.weather,
                selectedHour?.pop?.let { "降水概率 $it%" }).joinToString(" · "),
            color = palette.panelContent.copy(alpha = .8f), fontSize = 13.sp
        )
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp)) {
            Box(Modifier.width((hours.size * 54).dp).height(95.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = 54.dp.toPx()
                    fun y(value: Double) = 38.dp.toPx() + ((high - value) / (high - low).coerceAtLeast(6.0)).toFloat() * 44.dp.toPx()
                    val path = Path()
                    // Missing observations break the line instead of becoming a synthetic zero.
                    var previous: Offset? = null
                    hours.forEachIndexed { index, hour ->
                        val value = hour.temperature
                        if (value == null) { previous = null } else {
                            val point = Offset(cell * (index + .5f), y(value))
                            val last = previous
                            if (last == null) path.moveTo(point.x, point.y)
                            else path.cubicTo((last.x + point.x) / 2, last.y, (last.x + point.x) / 2, point.y, point.x, point.y)
                            previous = point
                        }
                    }
                    drawPath(path, palette.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    hours.getOrNull(selected)?.temperature?.let {
                        drawCircle(palette.panelContent, 3.dp.toPx(), Offset(cell * (selected + .5f), y(it)))
                    }
                }
                Row {
                    hours.forEachIndexed { index, hour ->
                        val y = hour.temperature?.let { ((high - it) / (high - low).coerceAtLeast(6.0) * 44).toFloat() } ?: 0f
                        Box(Modifier.width(54.dp).height(95.dp).clickable { selected = index }, contentAlignment = Alignment.TopCenter) {
                            Text(weatherDegrees(hour.temperature), Modifier.offset(y = (y + 10).dp), fontSize = 18.sp, color = palette.panelContent)
                        }
                    }
                }
            }
            Row {
                hours.forEachIndexed { index, hour ->
                    Column(
                        Modifier.width(54.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (selected == index) palette.panelContent.copy(alpha = .08f) else Color.Transparent)
                            .clickable { selected = index }.padding(vertical = 8.dp)
                            .semantics { contentDescription = "${hour.time}，${hour.weather}，${weatherDegrees(hour.temperature)}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val daylight = hour.time?.let { hourOf(it)?.let { hour -> hour in 6..18 } } ?: (weather.isDaylight != false)
                        WeatherSymbol(hour.weather, daylight, Modifier.size(25.dp))
                        Text(hour.windScale?.let { if (it.endsWith("级")) it else "${it}级" } ?: "—", color = palette.muted, fontSize = 11.sp, maxLines = 1)
                        Text(hour.time?.let(::hourLabel).orEmpty(), color = palette.panelContent, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        if (detailed) {
            hours.forEach { hour ->
                HorizontalDivider(color = palette.panelContent.copy(alpha = .08f))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hour.time?.let(::clockLabel).orEmpty(), Modifier.width(58.dp), color = palette.panelContent, fontSize = 14.sp)
                    WeatherSymbol(hour.weather, hour.time?.let { hourOf(it)?.let { hour -> hour in 6..18 } }
                        ?: (weather.isDaylight != false), Modifier.size(25.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(hour.weather.orEmpty(), color = palette.panelContent, fontSize = 14.sp)
                        Text(listOfNotNull(hour.windDirection, hour.windScale?.let { if (it.endsWith("级")) it else "${it}级" },
                            hour.humidity?.let { "湿度 $it%" }, hour.pop?.let { "降水 $it%" }).joinToString(" · "), color = palette.muted, fontSize = 11.sp)
                    }
                    Text(weatherDegrees(hour.temperature), color = palette.panelContent, fontSize = 19.sp)
                }
            }
        }
    }
}

@Composable
internal fun WeatherRainPanel(weather: WeatherResponse, palette: WeatherPalette, onDetailsClick: (() -> Unit)? = null) {
    val next = weather.hourlyForecast.orEmpty().take(6)
    val rainy = next.any { it.weather.orEmpty().let { text -> "雨" in text || "雪" in text } || (it.pop ?: 0) >= 40 || (it.precip ?: 0.0) > 0 }
    val hasForecast = next.any { !it.weather.isNullOrBlank() || it.pop != null || it.precip != null }
    val precipitationDescription = weather.precipitationDescription?.takeIf { it.isNotBlank() }
    WeatherPanel(palette, if (onDetailsClick != null) Modifier.clickable(onClickLabel = "查看天气预报", onClick = onDetailsClick) else Modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (precipitationDescription != null) "降水预报" else "出行天气", fontSize = 14.sp, color = palette.muted)
                Text(precipitationDescription ?: when { !hasForecast -> "留意天气变化"; rainy -> "未来几小时可能有降水"; else -> "未来几小时暂无降水预报" }, fontSize = 18.sp, color = palette.panelContent)
                Text(when { precipitationDescription != null -> "出行前可再查看最新预报"; !hasForecast -> "逐小时预报暂不可用"; rainy -> "出门记得随身带伞"; else -> "出行前可再查看最新预报" }, fontSize = 12.sp, color = palette.muted)
            }
            WeatherSymbol(if (rainy) "雨" else weather.weather, weather.isDaylight != false,
                Modifier.padding(start = 18.dp).size(55.dp))
        }
    }
}

internal data class WeatherMetric(val title: String, val value: String, val caption: String, val progress: Float, val color: Color, val sun: Boolean = false)

internal fun weatherMetrics(weather: WeatherResponse): List<WeatherMetric> {
    val sunEvent = weather.forecast?.firstOrNull()?.let { currentSunEvent(it) }
    return listOfNotNull(
        weather.uv?.let { WeatherMetric("紫外线", uvLabel(it), "UV ${weatherNumber(it)}", (it / 12).toFloat(), Color(0xFFFFC852)) },
        weather.humidity?.let { WeatherMetric("湿度", "$it%", when { it >= 80 -> "潮湿"; it <= 30 -> "干燥"; else -> "适宜" }, it / 100f, Color(0xFF18B6F2)) },
        weather.feelsLike?.let { WeatherMetric("体感", weatherDegrees(it), "体感温度", ((it + 20) / 65).toFloat(), Color(0xFF42D6A1)) },
        weather.windPower?.let { WeatherMetric(weather.windDirection ?: "风力", if (it.endsWith("级")) it else "${it}级", "风力", (it.takeWhile(Char::isDigit).toFloatOrNull() ?: 0f) / 12, Color(0xFF39B5FB)) },
        weather.visibility?.let { WeatherMetric("能见度", "${weatherNumber(it)} km", "水平能见度", (it / 30).toFloat(), Color(0xFF9CDDD0)) },
        weather.pressure?.let { WeatherMetric("气压", "${it.roundToInt()}", "hPa", ((it - 950) / 100).toFloat(), Color(0xFFC1B8F0)) },
        sunEvent?.let { WeatherMetric(it.first, it.second, "今天", 0f, Color(0xFFFFD36D), sun = true) }
    )
}

@Composable
internal fun WeatherDetailsGrid(weather: WeatherResponse, palette: WeatherPalette) {
    val metrics = remember(weather) { weatherMetrics(weather) }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(palette.panel)
                            .heightIn(min = 108.dp).padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(metric.title, fontSize = 13.sp, color = Muted, maxLines = 1)
                            Text(metric.value, fontSize = 23.sp, color = Color.White, maxLines = 1, fontWeight = FontWeight.Normal)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (metric.sun) WeatherSymbol("晴", true, Modifier.size(50.dp))
                            else Canvas(Modifier.size(50.dp)) {
                                val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                                val inset = 3.dp.toPx()
                                val area = Size(size.width - inset * 2, size.height - inset * 2)
                                drawArc(Color.White.copy(alpha = .12f), 135f, 270f, false, Offset(inset, inset), area, style = stroke)
                                drawArc(metric.color, 135f, 270f * metric.progress.coerceIn(.025f, 1f), false, Offset(inset, inset), area, style = stroke)
                                val angle = Math.toRadians((135 + 270 * metric.progress.coerceIn(0f, 1f)).toDouble())
                                val center = Offset(size.width / 2, size.height / 2)
                                drawLine(Color.White, center, center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (size.width * .28f), 2.dp.toPx(), StrokeCap.Round)
                                drawCircle(metric.color, 3.dp.toPx(), center)
                            }
                            Text(metric.caption, fontSize = 9.sp, color = Muted, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun WeatherAirPanel(weather: WeatherResponse, palette: WeatherPalette, detailed: Boolean = false, onDetailsClick: (() -> Unit)? = null) {
    WeatherPanel(palette, if (onDetailsClick != null) Modifier.clickable(onClickLabel = "查看空气质量", onClick = onDetailsClick) else Modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (onDetailsClick != null) "空气质量 ›" else "空气质量", color = palette.muted, fontSize = 14.sp)
            Text("${weather.aqi}  ${weather.aqiCategory.orEmpty()}", color = palette.panelContent, fontSize = 20.sp)
        }
        Canvas(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp)) {
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF76DCA0), Color(0xFFF1D45E), Color(0xFFF39859), Color(0xFFD77DAD))), cornerRadius = CornerRadius(size.height))
            val point = Offset(((weather.aqi ?: 0) / 300f).coerceIn(.01f, .99f) * size.width, size.height / 2)
            drawCircle(palette.panel, 5.dp.toPx(), point)
            drawCircle(palette.panelContent, 3.dp.toPx(), point)
        }
        weather.aqiPrimary?.takeIf { it.isNotBlank() && it != "NA" }?.let { Text("主要污染物  $it", color = palette.muted, fontSize = 12.sp) }
        weather.airPollutants?.let { air ->
            val pollutants = (listOf("PM2.5" to air.pm25, "PM10" to air.pm10, "O₃" to air.o3, "NO₂" to air.no2) +
                if (detailed) listOf("SO₂" to air.so2, "CO" to air.co) else emptyList()).filter { it.second != null }
            pollutants.chunked(if (detailed) 3 else 4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = if (detailed) 14.dp else 0.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(weatherNumber(value), color = palette.panelContent, fontSize = if (detailed) 24.sp else 16.sp)
                            Text(label, color = palette.muted, fontSize = if (detailed) 13.sp else 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeatherLifePanel(indices: LifeIndices, palette: WeatherPalette) {
    val items = listOf("穿衣" to indices.clothing, "防晒" to indices.uv, "运动" to indices.exercise,
        "洗车" to indices.carWash, "雨伞" to indices.umbrella, "感冒" to indices.coldRisk)
        .mapIndexedNotNull { index, (label, item) -> item?.let { Triple(label, it, index) } }
    if (items.isEmpty()) return
    var selected by remember { mutableStateOf<Pair<String, LifeIndexItem>?>(null) }
    WeatherPanel(palette) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, item, icon) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable { selected = label to item }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LifeAdviceSymbol(icon, if (palette.breezy) palette.accent else null)
                        Text(label, color = palette.panelContent, fontSize = 13.sp)
                        Text(item.level.orEmpty(), color = palette.muted, fontSize = 11.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    selected?.let { (label, item) ->
        AppDialog(onDismiss = { selected = null },
            title = "$label · ${item.level.orEmpty()}",
            content = { Text(listOfNotNull(item.brief, item.advice).filter { it.isNotBlank() }.distinct().joinToString("\n\n")) },
            actions = listOf(AppDialogAction("知道了", onClick = { selected = null })))
    }
}

@Composable
private fun LifeAdviceSymbol(index: Int, monetColor: Color? = null) {
    val iconColor = monetColor ?: LocalContentColor.current.copy(alpha = .7f)
    if (index == 1 && monetColor == null) { WeatherSymbol("晴", true, Modifier.size(28.dp)); return }
    Canvas(Modifier.size(28.dp)) {
        val u = size.minDimension
        val stroke = Stroke(u * .06f, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, endX: Float, endY: Float) =
            drawLine(iconColor, Offset(x * u, y * u), Offset(endX * u, endY * u), u * .06f, StrokeCap.Round)
        when (index) {
            1 -> {
                drawCircle(iconColor, u * .22f, style = stroke)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4
                    val d = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(iconColor, center + d * u * .34f, center + d * u * .45f, u * .06f, StrokeCap.Round)
                }
            }
            0 -> {
                val shirt = Path().apply {
                    moveTo(u * .32f, u * .12f); lineTo(u * .1f, u * .27f); lineTo(u * .2f, u * .48f)
                    lineTo(u * .3f, u * .43f); lineTo(u * .3f, u * .88f); lineTo(u * .7f, u * .88f)
                    lineTo(u * .7f, u * .43f); lineTo(u * .8f, u * .48f); lineTo(u * .9f, u * .27f)
                    lineTo(u * .68f, u * .12f); quadraticTo(u * .5f, u * .35f, u * .32f, u * .12f); close()
                }
                drawPath(shirt, iconColor, style = stroke)
            }
            2 -> {
                drawCircle(iconColor, u * .1f, Offset(u * .6f, u * .14f))
                line(.4f, .32f, .57f, .36f); line(.57f, .36f, .72f, .54f); line(.57f, .36f, .44f, .62f)
                line(.44f, .62f, .25f, .86f); line(.44f, .62f, .67f, .73f); line(.67f, .73f, .77f, .91f)
                line(.4f, .32f, .23f, .48f)
            }
            3 -> {
                drawRoundRect(iconColor, Offset(u * .12f, u * .42f), Size(u * .76f, u * .35f), CornerRadius(u * .08f), style = stroke)
                line(.22f, .42f, .3f, .19f); line(.3f, .19f, .7f, .19f); line(.7f, .19f, .78f, .42f)
                line(.23f, .57f, .3f, .57f); line(.7f, .57f, .77f, .57f)
                drawCircle(iconColor, u * .07f, Offset(u * .26f, u * .83f)); drawCircle(iconColor, u * .07f, Offset(u * .74f, u * .83f))
            }
            4 -> {
                drawArc(iconColor, 180f, 180f, false, Offset(u * .08f, u * .14f), Size(u * .84f, u * .75f), style = stroke)
                line(.08f, .52f, .92f, .52f); line(.5f, .14f, .5f, .79f)
                drawArc(iconColor, 0f, 180f, false, Offset(u * .3f, u * .69f), Size(u * .2f, u * .2f), style = stroke)
            }
            else -> {
                drawCircle(iconColor, u * .39f, style = stroke)
                line(.5f, .27f, .5f, .73f); line(.27f, .5f, .73f, .5f)
            }
        }
    }
}

internal fun currentSunEvent(day: ForecastDay, now: Instant = Instant.now()): Pair<String, String>? {
    fun parse(value: String?): OffsetDateTime? = value?.let {
        runCatching { OffsetDateTime.parse(it) }.getOrNull() ?: runCatching {
            val date = day.date?.take(10)?.let(LocalDate::parse) ?: LocalDate.now(ZoneId.of("Asia/Shanghai"))
            LocalTime.parse(it).atDate(date).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime()
        }.getOrNull()
    }
    val rise = parse(day.sunrise) ?: return null
    val set = parse(day.sunset) ?: return null
    val showRise = now.isBefore(rise.toInstant()) || now.isAfter(set.toInstant())
    return (if (showRise) "日出" else "日落") to (if (showRise) rise else set).format(DateTimeFormatter.ofPattern("HH:mm"))
}

@Composable
internal fun WeatherSymbol(description: String?, daylight: Boolean, modifier: Modifier) {
    if (LocalBreezyWeatherStyle.current) {
        BreezyWeatherSymbol(description, daylight, modifier)
        return
    }
    val text = description.orEmpty()
    val placeholderColor = LocalContentColor.current.copy(alpha = .7f)
    Canvas(modifier.semantics { contentDescription = description ?: "天气" }) {
        val unit = size.minDimension
        if (listOf("晴", "云", "阴", "雨", "雪", "雹", "雷", "雾", "霾", "沙", "尘").none { it in text }) {
            drawLine(placeholderColor, Offset(unit * .3f, unit * .5f), Offset(unit * .7f, unit * .5f), unit * .05f, StrokeCap.Round)
            return@Canvas
        }
        val rain = "雨" in text || "雹" in text
        val snow = "雪" in text
        val dust = "沙" in text || "尘" in text
        val fog = "雾" in text || "霾" in text || dust
        val cloud = rain || snow || "云" in text || "阴" in text
        val sunCenter = Offset(unit * .49f, unit * .43f)
        if ((!cloud || "云" in text) && !dust) {
            if (daylight) {
                repeat(8) { index ->
                    val angle = index * Math.PI / 4
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(Color(0xFFFFCF55), sunCenter + direction * unit * .34f,
                        sunCenter + direction * unit * .43f, unit * .055f, StrokeCap.Round)
                }
                drawCircle(Brush.linearGradient(listOf(Color(0xFFFFE28B), Color(0xFFFFB92F))), unit * .24f, sunCenter)
            } else {
                val moon = Path().apply {
                    moveTo(unit * .62f, unit * .12f)
                    cubicTo(unit * .1f, unit * .08f, unit * .13f, unit * .83f, unit * .66f, unit * .72f)
                    cubicTo(unit * .83f, unit * .7f, unit * .9f, unit * .57f, unit * .91f, unit * .45f)
                    cubicTo(unit * .58f, unit * .6f, unit * .43f, unit * .35f, unit * .62f, unit * .12f)
                    close()
                }
                drawPath(moon, Brush.linearGradient(listOf(Color(0xFFFFE390), Color(0xFFFFC332))))
            }
        }
        if (cloud) {
            val cloudBrush = Brush.verticalGradient(listOf(Color(0xFFF6F8FA), Color(0xFF93A3B3)), startY = unit * .25f, endY = unit * .7f)
            val shape = Path().apply {
                moveTo(unit * .24f, unit * .7f)
                cubicTo(-unit * .03f, unit * .69f, unit * .04f, unit * .32f, unit * .31f, unit * .36f)
                cubicTo(unit * .37f, unit * .05f, unit * .78f, unit * .11f, unit * .78f, unit * .4f)
                cubicTo(unit * 1.02f, unit * .36f, unit * 1.04f, unit * .7f, unit * .8f, unit * .7f)
                close()
            }
            drawPath(shape, cloudBrush)
            drawPath(shape, Color.White.copy(alpha = .25f), style = Stroke(unit * .035f))
        }
        if (rain || snow) {
            repeat(if (snow) 3 else 2) { index ->
                val x = unit * (.34f + index * .21f)
                if (snow) drawCircle(Color(0xFFD7EFFF), unit * .035f, Offset(x, unit * .88f))
                else drawLine(Color(0xFF73D4F0), Offset(x, unit * .81f), Offset(x - unit * .035f, unit * .96f), unit * .065f, StrokeCap.Round)
            }
        }
        if (fog) {
            repeat(3) { index ->
                val y = unit * (.66f + index * .1f)
                drawLine((if (dust) Color(0xFFD8BF9A) else Color(0xFFB8C5CE)).copy(alpha = .9f - index * .2f), Offset(unit * (.12f + index * .1f), y), Offset(unit * (.9f - index * .1f), y), unit * .055f, StrokeCap.Round)
            }
        }
        if ("雷" in text) {
            val bolt = Path().apply {
                moveTo(unit * .53f, unit * .54f); lineTo(unit * .38f, unit * .83f)
                lineTo(unit * .53f, unit * .8f); lineTo(unit * .44f, unit)
                lineTo(unit * .72f, unit * .66f); lineTo(unit * .55f, unit * .7f); close()
            }
            drawPath(bolt, Color(0xFFFFD04F))
        }
    }
}

internal fun weatherDegrees(value: Double?) = value?.let { "${it.roundToInt()}°" } ?: "—"
internal fun weatherNumber(value: Double?) = value?.let { if (it == it.roundToInt().toDouble()) it.roundToInt().toString() else "%.1f".format(java.util.Locale.CHINA, it) } ?: "—"
private fun uvLabel(value: Double) = when { value < 3 -> "弱"; value < 6 -> "中等"; value < 8 -> "强"; value < 11 -> "很强"; else -> "极强" }
private fun clockLabel(time: String): String = time.substringAfter('T', time.substringAfter(' ', time)).take(5)
private fun hourOf(time: String): Int? = time.substringAfter('T', time.substringAfter(' ', time)).take(2).toIntOrNull()
private fun hourLabel(time: String): String = hourOf(time)?.let { "%02d:00".format(it) } ?: time.takeLast(5)
