package com.ahu.ahutong.ui.screen.main.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.weather.WeatherResponse
import com.ahu.ahutong.data.weather.WeatherAlert
import com.kyant.monet.dynamicColorScheme
import com.kyant.monet.n1
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
internal fun BreezyWeatherTheme(content: @Composable () -> Unit) {
    // dynamicColorScheme and the neutral tones inherit AHUTheme's LocalTonalPalettes seed.
    val colors = dynamicColorScheme(isLight = false).copy(
        background = 6.n1, onBackground = 90.n1,
        surface = 6.n1, onSurface = 90.n1,
        surfaceVariant = 30.n1, onSurfaceVariant = 80.n1,
        inverseSurface = 90.n1, inverseOnSurface = 20.n1,
        outline = 60.n1, outlineVariant = 30.n1,
        surfaceBright = 24.n1, surfaceDim = 6.n1,
        surfaceContainerLowest = 4.n1, surfaceContainerLow = 10.n1,
        surfaceContainer = 12.n1, surfaceContainerHigh = 17.n1,
        surfaceContainerHighest = 22.n1
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes, content = content)
}

/** Independently drawn Compose adaptation of Breezy's centered header and forecast/metric layout. */
@Composable
internal fun breezyWeatherPalette(weather: WeatherResponse?): WeatherPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    val night = weather?.isDaylight == false || (weather == null && dark)
    val sky = breezySkyColors(weather, night)
    return WeatherPalette(
        top = sky.first(), bottom = sky.last(),
        panel = scheme.surfaceContainerLow,
        content = if (night == dark) scheme.onSurface else scheme.inverseOnSurface,
        panelContent = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        breezy = true
    )
}

private fun breezySkyColors(weather: WeatherResponse?, night: Boolean): List<Color> {
    val condition = weather?.weather.orEmpty()
    return when {
        night -> listOf(Color(0xFF171D52), Color(0xFF3F4DBA), Color(0xFF5E68BD))
        listOf("雾", "霾", "沙", "尘").any { it in condition } -> listOf(Color(0xFFA2C3FF), Color(0xFFB3AFD1), Color(0xFFE7BEB0))
        "雪" in condition -> listOf(Color(0xFFBDCFEB), Color(0xFFD6D5E8), Color(0xFFF0E7E4))
        "雨" in condition -> listOf(Color(0xFF97B6D1), Color(0xFFA6BBCE), Color(0xFF97A8D7))
        "阴" in condition -> listOf(Color(0xFFAFBCC7), Color(0xFFD2D6DB), Color(0xFFEBEFF8))
        "云" in condition -> listOf(Color(0xFF64DBFF), Color(0xFFD6DEE0), Color(0xFFE2F0F6))
        else -> listOf(Color(0xFFFFBA5E), Color(0xFFFFC67E), Color(0xFFFFF0E2))
    }
}

@Composable
internal fun BreezyWeatherBackdrop(weather: WeatherResponse?, palette: WeatherPalette) {
    val night = weather?.isDaylight == false || (weather == null && MaterialTheme.colorScheme.background.luminance() < .5f)
    val colors = breezySkyColors(weather, night)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(colors))
        if (weather?.isDaylight != false) {
            // Large, static cloud discs preserve the reference's weather-colored sky.
            listOf(Offset(.12f, .12f), Offset(.56f, .08f), Offset(.93f, .18f)).forEach {
                drawCircle(Color.White.copy(alpha = .09f), size.width * .38f,
                    Offset(size.width * it.x, size.height * it.y))
            }
        } else {
            listOf(Offset(.08f, .12f), Offset(.29f, .24f), Offset(.65f, .16f), Offset(.88f, .32f),
                Offset(.47f, .38f), Offset(.16f, .48f), Offset(.76f, .46f)).forEachIndexed { index, point ->
                drawCircle(Color.White.copy(alpha = .3f), (if (index % 2 == 0) 1f else .6f).dp.toPx(),
                    Offset(size.width * point.x, size.height * point.y))
            }
        }
    }
}

@Composable
internal fun BreezyWeatherHero(weather: WeatherResponse, palette: WeatherPalette) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        weather.reportTime?.takeIf { it.isNotBlank() }?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BreezyGlyph("clock", palette.content.copy(alpha = .85f), Modifier.size(24.dp))
                Text("更新于 ${it.substringAfter('T', it.substringAfter(' ', it)).take(5)}",
                    color = palette.content.copy(alpha = .85f), fontSize = 14.sp)
            }
        }
        Text(weather.weather.orEmpty(), Modifier.padding(top = 16.dp),
            color = palette.content, fontSize = 22.sp, fontWeight = FontWeight.Light)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val small = maxWidth < 350.dp
            val unitWidth = if (small) 56.dp else 70.dp
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center) {
                Spacer(Modifier.width(unitWidth))
                Text(weather.temperature?.let { weatherDegrees(it).removeSuffix("°") } ?: "—",
                    color = palette.content, fontSize = if (small) 120.sp else 140.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-5).sp, lineHeight = if (small) 120.sp else 140.sp)
                Text("°C", Modifier.width(unitWidth).padding(top = 12.dp), color = palette.content,
                    fontSize = if (small) 42.sp else 50.sp, fontWeight = FontWeight.Bold)
            }
        }
        weather.feelsLike?.let {
            Text("体感温度 ${weatherDegrees(it)}", color = palette.content, fontSize = 22.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        val today = weather.forecast?.firstOrNull()
        val day = (weather.tempMax ?: today?.tempMax)?.let { "白天: ${weatherDegrees(it)}" }
        val night = (weather.tempMin ?: today?.tempMin)?.let { "晚间: ${weatherDegrees(it)}" }
        Text((if (weather.isDaylight == false) listOfNotNull(night, day) else listOfNotNull(day, night)).joinToString(" · "),
            color = palette.content, fontSize = 16.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
internal fun BreezyWeatherAlertsPanel(alerts: List<WeatherAlert>?, palette: WeatherPalette) {
    if (alerts.isNullOrEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val alertPalette = palette.copy(panel = scheme.errorContainer, panelContent = scheme.onErrorContainer,
        muted = scheme.onErrorContainer.copy(alpha = .8f))
    WeatherPanel(alertPalette) {
        alerts.forEach { alert ->
            var expanded by rememberSaveable(alert.title, alert.publishTime) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val warning = when {
                        "橙" in alert.level.orEmpty() + alert.title.orEmpty() -> Color(0xFFFFA000)
                        "黄" in alert.level.orEmpty() + alert.title.orEmpty() -> Color(0xFFFFEB3B)
                        "蓝" in alert.level.orEmpty() + alert.title.orEmpty() -> Color(0xFF70B5FF)
                        else -> scheme.onErrorContainer
                    }
                    BreezyGlyph("warning", warning, Modifier.size(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(alert.title.orEmpty(), color = scheme.onErrorContainer, fontSize = 16.sp)
                        alert.publishTime?.let { Text(it.replace('T', ' ').take(16),
                            color = alertPalette.muted, fontSize = 12.sp) }
                    }
                }
                if (expanded) {
                    alert.text?.let { Text(it, color = scheme.onErrorContainer, fontSize = 14.sp, lineHeight = 22.sp) }
                    alert.guidance.orEmpty().forEach { Text(it, color = alertPalette.muted, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
internal fun BreezyWeatherForecastPanel(
    weather: WeatherResponse,
    palette: WeatherPalette,
    fullForecast: Boolean = false,
    onMoreClick: (() -> Unit)? = null
) {
    val days = weather.forecast.orEmpty().take(if (fullForecast) 15 else 7)
    if (days.isEmpty()) return
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val points = days.mapIndexed { index, day ->
        BreezyTrendPoint(
            label = if (index == 0) "今天" else day.week?.replace("星期", "周").orEmpty(),
            date = day.date?.take(10)?.takeLast(5)?.replace('-', '/'),
            weather = day.weatherDay, nightWeather = day.weatherNight,
            upper = when (mode) { 1 -> day.aqi?.toDouble(); 2 -> day.windSpeedDay; else -> day.tempMax },
            lower = if (mode == 0) day.tempMin else null,
            caption = if (mode == 2) day.windDirDay else null
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WeatherPanel(palette) {
            val today = days.first()
            val summary = listOfNotNull(today.weatherDay, today.tempMin?.let { "${weatherDegrees(it)}—${weatherDegrees(today.tempMax)}" }).joinToString(" · ")
            BreezyPanelHeading("每日预报", "calendar", summary, palette, onMoreClick, "查看未来15天天气", mode)
            BreezyTrendTabs(mode, { mode = it }, palette, days.any { it.aqi != null }, days.any { it.windSpeedDay != null })
            BreezyTrendChart(points, mode, palette)
        }
        if (fullForecast) WeatherForecastPanel(weather, palette, fullForecast = true)
    }
}

@Composable
internal fun BreezyWeatherHourlyPanel(
    weather: WeatherResponse,
    palette: WeatherPalette,
    detailed: Boolean = false,
    onDetailsClick: (() -> Unit)? = null
) {
    val hours = weather.hourlyForecast.orEmpty().take(24)
    if (hours.isEmpty()) return
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val points = hours.map { hour ->
        val time = hour.time.orEmpty().substringAfter('T', hour.time.orEmpty().substringAfter(' '))
        BreezyTrendPoint(label = time.take(5), weather = hour.weather,
            daylight = time.take(2).toIntOrNull()?.let { it in 6..18 } ?: (weather.isDaylight != false),
            upper = when (mode) { 1 -> hour.aqi?.toDouble(); 2 -> hour.windSpeed; else -> hour.temperature },
            caption = if (mode == 2) hour.windDirection else null)
    }
    WeatherPanel(palette) {
        BreezyPanelHeading("逐小时预报", "clock", weather.precipitationDescription ?: listOfNotNull(hours.first().weather,
            hours.first().temperature?.let { weatherDegrees(it) }).joinToString(" · "),
            palette, onDetailsClick, "天气预报", mode)
        BreezyTrendTabs(mode, { mode = it }, palette, hours.any { it.aqi != null }, hours.any { it.windSpeed != null })
        BreezyTrendChart(points, mode, palette)
        if (detailed) hours.forEach { hour ->
            androidx.compose.material3.HorizontalDivider(color = palette.panelContent.copy(alpha = .1f))
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(hour.time.orEmpty().substringAfter('T').take(5), Modifier.width(54.dp), color = palette.panelContent, fontSize = 13.sp)
                BreezyWeatherSymbol(hour.weather, hour.time.orEmpty().substringAfter('T').take(2).toIntOrNull()?.let { it in 6..18 }
                    ?: (weather.isDaylight != false), Modifier.size(25.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(hour.weather.orEmpty(), color = palette.panelContent, fontSize = 14.sp)
                    Text(listOfNotNull(hour.windDirection, hour.windSpeed?.let { "${weatherNumber(it)} m/s" },
                        hour.aqi?.let { "AQI $it" }).joinToString(" · "), color = palette.muted, fontSize = 11.sp)
                }
                Text(weatherDegrees(hour.temperature), color = palette.panelContent, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BreezyPanelHeading(title: String, glyph: String, summary: String, palette: WeatherPalette,
    onClick: (() -> Unit)?, actionDescription: String, mode: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick)
                .semantics { contentDescription = actionDescription } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BreezyGlyph(glyph, palette.muted, Modifier.size(23.dp))
            Text(title, Modifier.weight(1f), color = palette.panelContent, fontSize = 18.sp)
            if (mode != 0) Text(if (mode == 1) "AQI" else "m/s", color = palette.muted, fontSize = 12.sp)
            if (onClick != null) Text("›", color = palette.muted, fontSize = 22.sp)
        }
        if (summary.isNotBlank()) Text(summary, color = palette.muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun BreezyTrendTabs(selected: Int, onSelect: (Int) -> Unit, palette: WeatherPalette, air: Boolean, wind: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("天气情况", "空气质量", "风况").forEachIndexed { index, label ->
            val enabled = index == 0 || (index == 1 && air) || (index == 2 && wind)
            Text(label, Modifier.weight(1f).clip(if (index == selected) CircleShape else RoundedCornerShape(10.dp))
                .background(if (index == selected) scheme.primary else scheme.surfaceContainerHigh)
                .clickable(enabled = enabled) { onSelect(index) }.padding(vertical = 13.dp),
                color = if (index == selected) scheme.onPrimary else palette.muted.copy(alpha = if (enabled) 1f else .45f),
                fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

private data class BreezyTrendPoint(val label: String, val date: String? = null, val weather: String? = null,
    val nightWeather: String? = null, val daylight: Boolean = true, val upper: Double?, val lower: Double? = null,
    val caption: String? = null)

@Composable
private fun BreezyTrendChart(points: List<BreezyTrendPoint>, mode: Int, palette: WeatherPalette) {
    val values = points.flatMap { listOfNotNull(it.upper, it.lower) }
    val low = values.minOrNull() ?: 0.0
    val high = values.maxOrNull() ?: low
    val range = (high - low).coerceAtLeast(when (mode) { 1 -> 25.0; 2 -> 3.0; else -> 8.0 })
    fun graphY(value: Double) = 28f + ((high - value) / range * 78).toFloat()
    fun label(value: Double?) = value?.let { if (mode == 0) weatherDegrees(it) else weatherNumber(it) } ?: "—"
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 7.dp)) {
        Row {
            points.forEach { point ->
                Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(point.label, color = palette.panelContent, fontSize = 14.sp, maxLines = 1)
                    point.date?.let { Text(it, color = palette.muted, fontSize = 11.sp) }
                    BreezyWeatherSymbol(point.weather, point.daylight, Modifier.padding(top = 4.dp).size(28.dp))
                }
            }
        }
        Box(Modifier.width((points.size * 68).dp).height(140.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = 68.dp.toPx()
                fun point(index: Int, value: Double) = Offset(cell * (index + .5f), graphY(value).dp.toPx())
                drawRect(palette.accent.copy(alpha = .025f), size = Size(cell, size.height))
                points.forEachIndexed { index, item ->
                    drawLine(palette.panelContent.copy(alpha = .06f), Offset(cell * (index + .5f), 20.dp.toPx()),
                        Offset(cell * (index + .5f), size.height - 12.dp.toPx()), 1.dp.toPx())
                    val next = points.getOrNull(index + 1)
                    if (item.upper != null && item.lower != null && next?.upper != null && next.lower != null) {
                        val band = Path().apply {
                            val a = point(index, item.upper); moveTo(a.x, a.y)
                            val b = point(index + 1, next.upper); lineTo(b.x, b.y)
                            val c = point(index + 1, next.lower); lineTo(c.x, c.y)
                            val d = point(index, item.lower); lineTo(d.x, d.y); close()
                        }
                        drawPath(band, palette.accent.copy(alpha = .1f))
                    }
                }
                listOf(true, false).forEach { upper ->
                    val path = Path()
                    var previous: Offset? = null
                    points.forEachIndexed { index, item ->
                        val value = if (upper) item.upper else item.lower
                        if (value == null) previous = null else {
                            val p = point(index, value)
                            if (previous == null) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                            previous = p
                        }
                    }
                    drawPath(path, palette.accent.copy(alpha = if (upper) 1f else .45f),
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Row {
                points.forEach { point ->
                    Box(Modifier.width(68.dp).height(140.dp), contentAlignment = Alignment.TopCenter) {
                        Text(label(point.upper), Modifier.offset(y = ((point.upper?.let(::graphY) ?: 28f) - 21).dp),
                            color = palette.panelContent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        point.lower?.let { Text(label(it), Modifier.offset(y = (graphY(it) + 6).dp), color = palette.muted, fontSize = 13.sp) }
                    }
                }
            }
        }
        Row {
            points.forEach { point ->
                Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (mode == 0 && point.nightWeather != null) BreezyWeatherSymbol(point.nightWeather, false, Modifier.size(25.dp))
                    point.caption?.let { Text(it, color = palette.muted, fontSize = 10.sp, maxLines = 1) }
                }
            }
        }
    }
}

private data class BreezyMetric(val title: String, val value: String, val unit: String, val subtitle: String,
    val kind: String, val progress: Float = 0f)

@Composable
internal fun BreezyWeatherDetailsGrid(weather: WeatherResponse, palette: WeatherPalette, onAirClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val sun = remember(weather.forecast) { weather.forecast?.firstOrNull()?.let { currentSunEvent(it) } }
    val metrics = listOfNotNull(
        weather.precipitation?.let { BreezyMetric("降水量", weatherNumber(it), "毫米", "当前降水量", "rain") },
        weather.windSpeed?.let { BreezyMetric("风况", weatherNumber(it), "m/s", weather.windDirection.orEmpty(), "wind") }
            ?: weather.windPower?.let { BreezyMetric("风况", it.removeSuffix("级"), "级", weather.windDirection.orEmpty(), "wind") },
        weather.aqi?.let { BreezyMetric("空气质量", it.toString(), "", weather.aqiCategory.orEmpty(), "air", it / 300f) },
        weather.humidity?.let { BreezyMetric("相对湿度", it.toString(), "%", "相对湿度", "humidity", it / 100f) },
        weather.uv?.let { BreezyMetric("紫外线", weatherNumber(it), "", weatherMetrics(weather).firstOrNull { m -> m.title == "紫外线" }?.value.orEmpty(), "sun", (it / 12).toFloat()) },
        weather.visibility?.let { BreezyMetric("能见度", weatherNumber(it), "公里", "水平能见度", "eye") },
        weather.pressure?.let { BreezyMetric("气压", weatherNumber(it), "hPa", "", "pressure", ((it - 963) / 100).toFloat()) },
        sun?.let { BreezyMetric(it.first, it.second, "", "今天", "sunEvent") }
    )
    val flower = remember { GenericShape { size, _ -> addPath(breezyFlower(size.width, size.height)) } }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { metric ->
                    val circle = metric.kind in listOf("wind", "air", "eye", "pressure")
                    val action = if (metric.kind == "air") onAirClick else null
                    BoxWithConstraints(Modifier.weight(1f).aspectRatio(1f)
                        .clip(if (metric.kind == "sun") flower else if (circle) CircleShape else RoundedCornerShape(28.dp))
                        .background(palette.panel)
                        .then(if (action != null) Modifier.clickable(onClick = action)
                            .semantics { contentDescription = "查看空气质量" } else Modifier), contentAlignment = Alignment.Center) {
                        val smallCard = maxWidth < 160.dp
                        Canvas(Modifier.fillMaxSize()) {
                            when (metric.kind) {
                                "sun" -> repeat(5) { index ->
                                    val angle = Math.PI * (.15 + index * .175)
                                    val position = Offset(size.width * (.5f + cos(angle).toFloat() * .34f),
                                        size.height * (.63f + sin(angle).toFloat() * .27f))
                                    drawCircle(palette.accent.copy(alpha = if (index == (metric.progress * 5).toInt().coerceIn(0, 4)) 1f else .18f),
                                        size.width * .035f, position)
                                }
                                "humidity" -> {
                                    val y = size.height * (1 - metric.progress.coerceIn(0f, 1f) * .9f)
                                    val wave = Path().apply {
                                        moveTo(0f, y)
                                        cubicTo(size.width * .16f, y - 6.dp.toPx(), size.width * .32f, y + 6.dp.toPx(), size.width * .5f, y)
                                        cubicTo(size.width * .66f, y - 6.dp.toPx(), size.width * .82f, y + 6.dp.toPx(), size.width, y)
                                        lineTo(size.width, size.height); lineTo(0f, size.height); close()
                                    }
                                    drawPath(wave, scheme.tertiaryContainer.copy(alpha = .8f))
                                }
                                "wind" -> {
                                    val fan = Path().apply {
                                        moveTo(size.width * .5f, size.height * .12f)
                                        cubicTo(size.width * .67f, size.height * .1f, size.width * .68f, size.height * .44f, size.width * .86f, size.height * .68f)
                                        cubicTo(size.width * .98f, size.height * .91f, size.width * .53f, size.height * .78f, size.width * .22f, size.height * .84f)
                                        cubicTo(-size.width * .02f, size.height * .83f, size.width * .23f, size.height * .33f, size.width * .5f, size.height * .12f)
                                        close()
                                    }
                                    val bearing = when (weather.windDirection) { "东北风" -> 45f; "东风" -> 90f; "东南风" -> 135f; "南风" -> 180f; "西南风" -> 225f; "西风" -> 270f; "西北风" -> 315f; else -> 0f }
                                    rotate(bearing) { drawPath(fan, scheme.secondaryContainer.copy(alpha = .8f)) }
                                }
                                "eye" -> drawPath(breezyFlower(size.width, size.height, .1f), scheme.secondaryContainer.copy(alpha = .7f))
                                "air", "pressure" -> {
                                    val inset = 10.dp.toPx()
                                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                                    val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                                    drawArc(palette.accent.copy(alpha = .12f), 135f, 270f, false, Offset(inset, inset), arc, style = stroke)
                                    drawArc(palette.accent, 135f, 270f * metric.progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = stroke)
                                }
                            }
                        }
                        val leftAligned = metric.kind == "rain" || metric.kind == "humidity"
                        Column(Modifier.fillMaxWidth().padding(if (metric.kind == "sunEvent") 12.dp else 15.dp),
                            horizontalAlignment = if (leftAligned) Alignment.Start else Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                BreezyGlyph(if (metric.kind == "sunEvent") "sun" else metric.kind, palette.accent, Modifier.size(21.dp))
                                Text(metric.title, color = palette.panelContent, fontSize = 16.sp, maxLines = 1)
                            }
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(metric.value, color = palette.panelContent,
                                    fontSize = if (metric.kind == "sunEvent") 27.sp else if (smallCard || metric.value.length > 7) 36.sp else 44.sp,
                                    maxLines = 1, letterSpacing = (-1).sp)
                                if (metric.unit.isNotEmpty() && metric.kind != "pressure") Text(metric.unit, Modifier.padding(bottom = 5.dp),
                                    color = palette.panelContent, fontSize = if (metric.unit == "%") 34.sp else if (metric.unit.length > 2) 17.sp else 20.sp)
                            }
                            if ((metric.subtitle.isNotBlank() && metric.kind != "humidity") || metric.kind == "pressure") Text(
                                if (metric.kind == "pressure") metric.unit else metric.subtitle,
                                color = palette.muted, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun breezyFlower(width: Float, height: Float, inset: Float = .015f): Path = Path().apply {
    val base = minOf(width, height) * (.5f - inset)
    repeat(97) { index ->
        val angle = index * Math.PI * 2 / 96
        val radius = base * (.94f + .06f * cos(angle * 10).toFloat())
        val x = width / 2 + cos(angle).toFloat() * radius
        val y = height / 2 + sin(angle).toFloat() * radius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
private fun BreezyGlyph(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension
        val stroke = Stroke(u * .075f, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color,
            Offset(x * u, y * u), Offset(xx * u, yy * u), u * .075f, StrokeCap.Round)
        when (kind) {
            "calendar" -> {
                drawRoundRect(color, Offset(u * .15f, u * .2f), Size(u * .7f, u * .7f), androidx.compose.ui.geometry.CornerRadius(u * .04f), style = stroke)
                line(.15f, .4f, .85f, .4f); line(.3f, .1f, .3f, .27f); line(.7f, .1f, .7f, .27f)
            }
            "clock" -> {
                drawCircle(color, u * .41f, style = stroke)
                line(.5f, .5f, .5f, .22f); line(.5f, .5f, .71f, .65f)
            }
            "pressure" -> {
                drawCircle(color, u * .41f, style = stroke)
                repeat(5) { index ->
                    val angle = Math.PI * (1 + index / 4.0)
                    val d = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(color, center + d * u * .27f, center + d * u * .32f, u * .055f, StrokeCap.Round)
                }
                line(.5f, .55f, .69f, .32f)
                drawCircle(color, u * .055f, Offset(u * .5f, u * .55f))
            }
            "warning" -> {
                drawPath(Path().apply { moveTo(u * .5f, u * .08f); lineTo(u * .94f, u * .88f); lineTo(u * .06f, u * .88f); close() }, color)
            }
            "wind" -> repeat(4) { index ->
                rotate(index * 90f) {
                    drawPath(Path().apply { moveTo(u * .5f, u * .5f); cubicTo(u * .1f, u * .55f, u * .13f, 0f, u * .51f, u * .1f); lineTo(u * .5f, u * .5f) }, color, style = stroke)
                }
            }
            "air" -> repeat(4) { row -> repeat(4) { col ->
                drawCircle(color, u * .035f, Offset(u * (.2f + col * .2f), u * (.2f + row * .2f)))
            } }
            "humidity", "rain" -> {
                val drop = Path().apply {
                    moveTo(u * .5f, u * .06f); cubicTo(u * .4f, u * .28f, u * .12f, u * .48f, u * .2f, u * .7f)
                    cubicTo(u * .28f, u, u * .73f, u, u * .81f, u * .7f)
                    cubicTo(u * .9f, u * .47f, u * .6f, u * .25f, u * .5f, u * .06f); close()
                }
                drawPath(drop, color, style = stroke)
                if (kind == "humidity") line(.32f, .72f, .66f, .4f)
            }
            "eye" -> {
                drawPath(Path().apply { moveTo(u * .05f, u * .5f); cubicTo(u * .3f, 0f, u * .7f, 0f, u * .95f, u * .5f)
                    cubicTo(u * .7f, u, u * .3f, u, u * .05f, u * .5f); close() }, color, style = stroke)
                drawCircle(color, u * .14f)
            }
            else -> {
                drawCircle(color, u * .22f, style = stroke)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(color, center + direction * u * .34f, center + direction * u * .45f, u * .065f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun BreezyWeatherSymbol(description: String?, daylight: Boolean, modifier: Modifier) {
    val text = description.orEmpty()
    val unknown = androidx.compose.material3.LocalContentColor.current.copy(alpha = .6f)
    Canvas(modifier.semantics { contentDescription = description ?: "天气" }) {
        val u = size.minDimension
        if (listOf("晴", "云", "阴", "雨", "雪", "雹", "雷", "雾", "霾", "沙", "尘").none { it in text }) {
            drawLine(unknown, Offset(u * .3f, u * .5f), Offset(u * .7f, u * .5f), u * .05f, StrokeCap.Round)
            return@Canvas
        }
        val rain = "雨" in text || "雹" in text
        val snow = "雪" in text
        val dust = "沙" in text || "尘" in text
        val cloud = rain || snow || "云" in text || "阴" in text
        if ((!cloud || "云" in text) && !dust) {
            if (daylight) {
                val star = Path().apply {
                    repeat(16) { index ->
                        val angle = index * Math.PI / 8
                        val radius = u * if (index % 2 == 0) .44f else .33f
                        val x = u * .48f + cos(angle).toFloat() * radius
                        val y = u * .45f + sin(angle).toFloat() * radius
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }; close()
                }
                drawPath(star, Color(0xFFFFCC62)); drawCircle(Color(0xFFFFE18A), u * .29f, Offset(u * .48f, u * .45f))
            } else {
                drawPath(Path().apply {
                    moveTo(u * .62f, u * .08f); cubicTo(u * .1f, u * .06f, u * .1f, u * .86f, u * .68f, u * .77f)
                    cubicTo(u * .84f, u * .74f, u * .94f, u * .58f, u * .94f, u * .48f)
                    cubicTo(u * .6f, u * .62f, u * .43f, u * .3f, u * .62f, u * .08f); close()
                }, Color(0xFFC4DEFF))
            }
        }
        if (cloud) {
            drawRoundRect(Color(0xFFD4D6D8), Offset(u * .07f, u * .43f), Size(u * .86f, u * .3f), androidx.compose.ui.geometry.CornerRadius(u * .15f))
            drawCircle(Color(0xFFE7E7E7), u * .22f, Offset(u * .42f, u * .38f))
            drawCircle(Color(0xFFDEDFE0), u * .17f, Offset(u * .68f, u * .47f))
            drawCircle(Color(0xFFCBCDCE), u * .15f, Offset(u * .23f, u * .56f))
        }
        if (rain || snow) repeat(3) { index ->
            val x = u * (.26f + index * .23f)
            if (snow) drawCircle(Color(0xFFC4DEFF), u * .035f, Offset(x, u * .88f))
            else drawPath(Path().apply {
                moveTo(x, u * .77f); quadraticTo(x - u * .09f, u * .94f, x, u * .96f)
                quadraticTo(x + u * .09f, u * .94f, x, u * .77f); close()
            }, Color(0xFF438AFF))
        }
        if ("雾" in text || "霾" in text || dust) repeat(3) { index ->
            val y = u * (.67f + index * .1f)
            drawLine(if (dust) Color(0xFFB9A17F) else Color(0xFF9EA7AE), Offset(u * (.1f + index * .1f), y),
                Offset(u * (.9f - index * .1f), y), u * .065f, StrokeCap.Round)
        }
        if ("雷" in text) drawPath(Path().apply {
            moveTo(u * .55f, u * .54f); lineTo(u * .38f, u * .8f); lineTo(u * .52f, u * .77f)
            lineTo(u * .45f, u); lineTo(u * .73f, u * .65f); lineTo(u * .56f, u * .7f); close()
        }, Color(0xFFFFCC62))
    }
}
