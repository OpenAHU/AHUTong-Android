package com.ahu.ahutong.data.weather

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.http.GET
import retrofit2.http.Query

internal interface XiaomiLifeAdviceService {
    @GET("v1/page")
    suspend fun advice(
        @Query("locationKey") key: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("isLocated") isLocated: Boolean,
        @Query("channelId") channelId: String = "1050",
        @Query("language") language: String = "zh",
        @Query("country") country: String = "CN",
        // Fixed protocol compatibility values; no device identity or advertising identifiers.
        @Query("appVersion") appVersion: String = "17000328",
        @Query("sysVersion") sysVersion: String = "16"
    ): JsonObject
}

internal object XiaomiLifeAdvice {
    suspend fun load(
        service: XiaomiLifeAdviceService,
        location: XiaomiWeatherLocation,
        isLocated: Boolean
    ): LifeIndices? = try {
        withTimeoutOrNull(3_000) {
            parse(service.advice(location.key, location.latitude, location.longitude, isLocated))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Advice is optional: its network or format failure must not hide the weather.
        null
    }

    fun parse(json: JsonObject): LifeIndices? {
        if (json.text("status") != "0") return null
        val items = mutableMapOf<String, LifeIndexItem>()
        json.array("cards")?.forEach { card ->
            card.obj().array("list")?.forEach item@{ item ->
                val node = item.obj() ?: return@item
                if (node.text("type") !in listOf("3", "6") || node.text("template") != "13") return@item
                val data = node.get("data").obj() ?: return@item
                val type = data.text("wtrIndexType") ?: data.text("wtrStatKey") ?: return@item
                if (type !in listOf("clothing", "uvIndex", "sports", "carWash", "umbrella", "gm")) return@item
                val title = data.text("wtrTitle") ?: return@item
                val summary = data.text("wtrSummary")
                items.putIfAbsent(type, LifeIndexItem(level = title, brief = summary, advice = summary))
            }
        }
        if (items.isEmpty()) return null
        return LifeIndices(
            clothing = items["clothing"], uv = items["uvIndex"], exercise = items["sports"],
            carWash = items["carWash"], umbrella = items["umbrella"], coldRisk = items["gm"]
        )
    }

    private fun JsonElement?.obj(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject?.array(name: String): JsonArray? =
        this?.get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject?.text(name: String): String? = this?.get(name)?.takeIf { it.isJsonPrimitive }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() && it !in listOf("null", "--", "暂无数据") }
}
