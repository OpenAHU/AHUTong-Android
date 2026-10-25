package com.ahu.ahutong.data.weather

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

internal data class XiaomiWeatherLocation(
    val key: String,
    val name: String,
    val affiliation: String,
    val latitude: Double,
    val longitude: Double
)

/** Adapts the stock client's V7 response without retaining its raw location or device metadata. */
internal object XiaomiWeatherParser {
    private val formatToken = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[A-Za-z]")
    private val distanceTemplate = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[dsf]\\s*(?:公里|千米)外")

    fun locations(json: JsonArray): List<XiaomiWeatherLocation> = json.mapNotNull { item ->
        val city = item.objectOrNull()?.takeIf { it.success() } ?: return@mapNotNull null
        val key = city.text("locationKey") ?: return@mapNotNull null
        val name = city.text("name") ?: return@mapNotNull null
        val latitude = city.number("latitude")?.takeIf { it in -90.0..90.0 }
            ?: return@mapNotNull null
        val longitude = city.number("longitude")?.takeIf { it in -180.0..180.0 }
            ?: return@mapNotNull null
        if (!XiaomiWeatherClient.isProviderKey(key)) return@mapNotNull null
        XiaomiWeatherLocation(key, name, city.text("affiliation").orEmpty(), latitude, longitude)
    }

    fun parse(
        json: JsonObject,
        location: XiaomiWeatherLocation,
        now: Instant = Instant.now()
    ): WeatherResponse {
        val current = json.obj("current").takeIf { it.success() }
        val temperature = current.measurement("temperature")?.takeIf { it in -100.0..100.0 }
        if (!json.success() || current == null || temperature == null) {
            throw IOException("天气数据暂不可用，请稍后重试")
        }
        val daily = daily(json.obj("forecastDaily"))
        val currentDate = timestamp(current.text("pubTime"))?.toLocalDate()?.toString()
        val today = daily.firstOrNull { it.date == currentDate } ?: daily.firstOrNull()
        val aqi = json.obj("aqi").takeIf { it.success() }
        val aqiValue = aqi.number("aqi")?.toInt()?.takeIf { it >= 0 }
        val aqiLevel = aqiValue?.let {
            when { it <= 50 -> 1; it <= 100 -> 2; it <= 150 -> 3
                it <= 200 -> 4; it <= 300 -> 5; else -> 6 }
        }
        val parts = location.affiliation.split(',', '，').map(String::trim).filter(String::isNotBlank)
        val parentCity = parts.firstOrNull { it.endsWith("市") }
        val city = if (location.name.endsWith("市")) location.name else parentCity ?: location.name
        val brands = listOf(json, aqi).flatMap { section ->
            section.obj("brandInfo").array("brands")?.mapNotNull {
                val names = it.objectOrNull().obj("names")
                names.text("zh_CN") ?: names.text("en_US") ?: names.text("zh_TW")
            }.orEmpty()
        }.distinct()
        val precipitation = json.obj("minutely").takeIf { it.number("status") == 0.0 }
            .obj("precipitation").takeIf { it.number("status") == 0.0 }
        val wind = current.obj("wind").takeIf { it.success() }
        val windSpeed = wind.obj("speed").takeIf { it.success() }.number("value")?.takeIf { it >= 0 }
        return WeatherResponse(
            province = parts.dropLast(1).lastOrNull(),
            city = city,
            district = location.name.takeUnless { it == city },
            adcode = location.key,
            weather = weatherName(current.text("weather")),
            // V7 codes are not QWeather icon codes used by the existing home widget.
            weatherCode = null,
            temperature = temperature,
            windDirection = windDirection(wind.obj("direction").takeIf { it.success() }.number("value")),
            windPower = windLevel(windSpeed)?.let { "${it}级" },
            windSpeed = windSpeed?.div(3.6),
            humidity = current.measurement("humidity")?.toInt()?.takeIf { it in 0..100 },
            reportTime = current.text("pubTime"),
            feelsLike = current.measurement("feelsLike"),
            pressure = current.measurement("pressure")?.takeIf { it >= 0 }?.let {
                when (current.obj("pressure").text("unit")) {
                    "Pa" -> it / 100.0
                    "kPa" -> it * 10.0
                    else -> it
                }
            },
            visibility = current.measurement("visibility")?.takeIf { it >= 0 }?.let {
                if (current.obj("visibility").text("unit") == "m") it / 1000.0 else it
            },
            uv = current.number("uvIndex")?.takeIf { it >= 0 },
            precipitation = current.measurement("precipitation")?.takeIf { it >= 0 },
            cloud = current.measurement("cloud")?.toInt()?.takeIf { it in 0..100 },
            aqi = aqiValue,
            aqiLevel = aqiLevel,
            aqiCategory = aqiLevel?.let { listOf("优", "良", "轻度污染", "中度污染", "重度污染", "严重污染")[it - 1] },
            aqiPrimary = aqi.text("primary"),
            airPollutants = aqi?.let {
                AirPollutants(it.pollutant("pm25"), it.pollutant("pm10"), it.pollutant("o3"),
                    it.pollutant("no2"), it.pollutant("so2"), it.pollutant("co"))
            },
            alerts = alerts(json.array("alerts")).takeIf { it.isNotEmpty() },
            tempMax = today?.tempMax,
            tempMin = today?.tempMin,
            forecast = daily.takeIf { it.isNotEmpty() },
            hourlyForecast = hourly(json.obj("forecastHourly")).takeIf { it.isNotEmpty() },
            // Numeric life-index values alone do not describe their provider-specific categories.
            lifeIndices = null,
            source = (listOf("小米天气") + brands).joinToString(" · "),
            isDaylight = daylight(json.obj("forecastDaily"), now),
            precipitationDescription = rainDescription(precipitation)
        )
    }

    private fun rainDescription(precipitation: JsonObject?): String? {
        val distance = precipitation.number("kmNum")?.takeIf { it > 0 }
        val distanceLabel = distance?.let {
            java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() + "公里外"
        } ?: "附近"
        return listOfNotNull(precipitation.text("shortDescription"), precipitation.text("description"))
            .map { distanceTemplate.replace(it, distanceLabel) }
            .firstOrNull { !formatToken.containsMatchIn(it) }
    }

    private fun daily(json: JsonObject?): List<ForecastDay> {
        if (!json.success()) return emptyList()
        val date = timestamp(json.text("pubTime"))?.toLocalDate() ?: return emptyList()
        val temperatures = json.values("temperature")
        val weather = json.values("weather")
        val sun = json.values("sunRiseSet")
        val direction = json.obj("wind").takeIf { it.success() }.values("direction")
        val speed = json.obj("wind").takeIf { it.success() }.values("speed")
        val aqi = json.obj("aqi").takeIf { it.number("status") == 0.0 }
        val aqiDate = timestamp(aqi.text("pubTime"))?.toLocalDate()
        val count = listOf(temperatures, weather, sun, direction, speed).maxOf { it?.size() ?: 0 }
            .coerceAtMost(15)
        return (0 until count).map { index ->
            val day = date.plusDays(index.toLong())
            val aqiIndex = aqiDate?.let { day.toEpochDay() - it.toEpochDay() }
                ?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt()
            val daySpeed = speed.item(index).objectOrNull().number("from")?.takeIf { it >= 0 }
            val nightSpeed = speed.item(index).objectOrNull().number("to")?.takeIf { it >= 0 }
            ForecastDay(
                date = day.toString(),
                week = "星期" + "一二三四五六日"[day.dayOfWeek.value - 1],
                tempMax = temperatures.item(index).objectOrNull().number("from"),
                tempMin = temperatures.item(index).objectOrNull().number("to"),
                weatherDay = weatherName(weather.item(index).objectOrNull().text("from")),
                weatherNight = weatherName(weather.item(index).objectOrNull().text("to")),
                windDirDay = windDirection(direction.item(index).objectOrNull().number("from")),
                windDirNight = windDirection(direction.item(index).objectOrNull().number("to")),
                windScaleDay = windLevel(daySpeed)?.toString(),
                windScaleNight = windLevel(nightSpeed)?.toString(),
                windSpeedDay = daySpeed?.div(3.6),
                sunrise = sun.item(index).objectOrNull().text("from"),
                sunset = sun.item(index).objectOrNull().text("to"),
                aqi = aqiIndex?.let { aqi.array("value").item(it).aqiValue() }
            )
        }
    }

    private fun hourly(json: JsonObject?): List<HourlyForecast> {
        if (!json.success()) return emptyList()
        val hours = sortedMapOf<Instant, HourlyForecast>()
        fun read(name: String, apply: (HourlyForecast, JsonElement) -> HourlyForecast) {
            val block = json.obj(name).takeIf { it.success() } ?: return
            val start = timestamp(block.text("pubTime") ?: json.text("pubTime"))
            block.array("value")?.take(48)?.forEachIndexed { index, value ->
                if (value.isJsonObject && !value.asJsonObject.success()) return@forEachIndexed
                val time = timestamp(value.objectOrNull().text("datetime"))
                    ?: start?.plusHours(index.toLong()) ?: return@forEachIndexed
                val key = time.toInstant()
                val row = apply(hours[key] ?: HourlyForecast(time = time.toString()), value)
                if (row.temperature != null || row.weather != null || row.windSpeed != null ||
                    row.windDirection != null || row.pop != null || row.aqi != null
                ) hours[key] = row
            }
        }
        read("temperature") { row, value -> row.copy(temperature = value.number()) }
        read("weather") { row, value -> row.copy(weather = weatherName(value.text())) }
        read("precipitationProbability") { row, value ->
            row.copy(pop = value.number()?.toInt()?.takeIf { it in 0..100 })
        }
        read("wind") { row, value ->
            val wind = value.objectOrNull()
            val speed = wind.number("speed")?.takeIf { it >= 0 }
            row.copy(windDirection = windDirection(wind.number("direction")),
                windSpeed = speed?.div(3.6), windScale = windLevel(speed)?.toString())
        }
        // AQI must carry its own clock; reusing the temperature clock can shift its values.
        val aqi = json.obj("aqi")
        if (aqi.number("status") == 0.0 && timestamp(aqi.text("pubTime")) != null) {
            read("aqi") { row, value -> row.copy(aqi = value.aqiValue()) }
        }
        return hours.values.take(48)
    }

    private fun daylight(daily: JsonObject?, now: Instant): Boolean? {
        if (!daily.success()) return null
        daily.values("sunRiseSet")?.forEach { value ->
            val rise = timestamp(value.objectOrNull().text("from")) ?: return@forEach
            val set = timestamp(value.objectOrNull().text("to")) ?: return@forEach
            if (rise.toLocalDate() == now.atOffset(rise.offset).toLocalDate() && set.isAfter(rise)) {
                return now >= rise.toInstant() && now < set.toInstant()
            }
        }
        return null
    }

    private fun alerts(json: JsonArray?): List<WeatherAlert> = json?.mapNotNull { value ->
        val alert = value.objectOrNull().takeIf { it.success() } ?: return@mapNotNull null
        if (alert.text("title") == null && alert.text("detail") == null) return@mapNotNull null
        WeatherAlert(title = alert.text("title"), type = alert.text("type"), level = alert.text("level"),
            text = alert.text("detail"), publishTime = alert.text("pubTime"),
            publisher = alert.text("publisher"),
            guidance = alert.array("defense")?.mapNotNull { it.objectOrNull().text("defenseText") }
                ?.takeIf { it.isNotEmpty() })
    }.orEmpty()

    internal fun weatherName(code: String?): String? = when (code?.toIntOrNull()) {
        0 -> "晴"; 1 -> "多云"; 2 -> "阴"; 3 -> "阵雨"; 4 -> "雷阵雨"; 5 -> "雷阵雨伴冰雹"
        6 -> "雨夹雪"; 7 -> "小雨"; 8 -> "中雨"; 9 -> "大雨"; 10 -> "暴雨"; 11 -> "大暴雨"
        12 -> "特大暴雨"; 13 -> "阵雪"; 14 -> "小雪"; 15 -> "中雪"; 16 -> "大雪"; 17 -> "暴雪"
        18 -> "雾"; 19 -> "冻雨"; 20 -> "沙尘暴"; 21 -> "小到中雨"; 22 -> "中到大雨"
        23 -> "大到暴雨"; 24 -> "暴雨到大暴雨"; 25 -> "大暴雨到特大暴雨"
        26 -> "小到中雪"; 27 -> "中到大雪"; 28 -> "大到暴雪"; 29 -> "浮尘"; 30 -> "扬沙"
        31 -> "强沙尘暴"; 32 -> "浓雾"; 33 -> "雪"; 49 -> "强浓雾"; 53 -> "霾"
        54 -> "中度霾"; 55 -> "重度霾"; 56 -> "严重霾"; 57 -> "大雾"; 58 -> "特强浓雾"
        301 -> "雨"; 99 -> "未知"; else -> null
    }

    private fun windDirection(degrees: Double?): String? = when {
        degrees == null -> null
        degrees == 361.0 -> "旋转风"
        degrees !in 0.0..360.0 -> null
        else -> listOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风")[
            ((degrees + 22.5) / 45).toInt() % 8
        ]
    }

    private fun windLevel(kph: Double?): Int? {
        if (kph == null || kph < 0) return null
        return listOf(1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 117)
            .indexOfFirst { kph < it }.takeIf { it >= 0 } ?: 12
    }

    private fun timestamp(value: String?): OffsetDateTime? =
        value?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    private fun JsonElement?.objectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject?.obj(name: String): JsonObject? = this?.get(name).objectOrNull()
    private fun JsonObject?.array(name: String): JsonArray? =
        this?.get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonArray?.item(index: Int): JsonElement? =
        this?.takeIf { index in 0 until it.size() }?.get(index)
    private fun JsonElement?.text(): String? = this?.takeIf { it.isJsonPrimitive }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    private fun JsonObject?.text(name: String): String? = this?.get(name).text()
    private fun JsonElement?.number(): Double? = text()?.toDoubleOrNull()?.takeIf(Double::isFinite)
    private fun JsonElement?.aqiValue(): Int? = number()
        ?.takeIf { it in 0.0..Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }?.toInt()
    private fun JsonObject?.number(name: String): Double? = this?.get(name).number()
    private fun JsonObject?.success(): Boolean =
        this != null && (!has("status") || number("status") == 0.0)
    private fun JsonObject?.values(name: String): JsonArray? = obj(name).takeIf { it.success() }.array("value")
    private fun JsonObject?.measurement(name: String): Double? = obj(name).takeIf { it.success() }.number("value")
    private fun JsonObject?.pollutant(name: String): Double? = number(name)?.takeIf { it >= 0 }
}
