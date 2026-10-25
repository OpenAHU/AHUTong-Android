package com.ahu.ahutong.data.weather

import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.network.retrofit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    suspend fun getWeather(city: String? = null, adcode: String? = null): WeatherResponse

    suspend fun getWeatherAt(latitude: Double, longitude: Double): WeatherResponse

    companion object {
        val API: WeatherApi by lazy {
            val client = AhuHttp.plain(connectTimeoutSeconds = 10, readTimeoutSeconds = 15).build()
            XiaomiWeatherClient(
                retrofit("https://weatherapi.market.xiaomi.com/", client)
                    .create(XiaomiWeatherService::class.java),
                retrofit(
                    "https://wtradv.market.xiaomi.com/",
                    client.newBuilder().retryOnConnectionFailure(false)
                        .callTimeout(3, TimeUnit.SECONDS).build()
                ).create(XiaomiLifeAdviceService::class.java)
            )
        }
    }
}

internal interface XiaomiWeatherService {
    @GET("wtr-v3/location/city/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("appKey") appKey: String = APP_KEY,
        @Query("locale") locale: String = "zh_CN"
    ): JsonArray

    @GET("wtr-v3/location/city/info")
    suspend fun cityInfo(
        @Query("locationKey") key: String,
        @Query("appKey") appKey: String = APP_KEY,
        @Query("locale") locale: String = "zh_CN"
    ): JsonArray

    @GET("wtr-v3/location/city/geo")
    suspend fun locate(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("appKey") appKey: String = APP_KEY,
        @Query("locale") locale: String = "zh_CN"
    ): JsonArray

    @GET("wtr-v3/weather/all")
    suspend fun weather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("locationKey") key: String,
        @Query("isLocated") isLocated: Boolean,
        @Query("days") days: Int = 15,
        @Query("appKey") appKey: String = APP_KEY,
        @Query("locale") locale: String = "zh_CN",
        @Query("isGlobal") isGlobal: Boolean = false,
        // The stock APK's fixed client protocol parameter; no account or device credential.
        @Query("sign") sign: String = "zUFJoAR2ZVrDy1vF3D07"
    ): JsonObject

    companion object {
        const val APP_KEY = "weather20151024"
    }
}

internal class WeatherQueryException(message: String) : IOException(message)

internal class XiaomiWeatherClient(
    private val service: XiaomiWeatherService,
    private val auxService: XiaomiLifeAdviceService? = null
) : WeatherApi {
    override suspend fun getWeather(city: String?, adcode: String?): WeatherResponse {
        val location = when {
            !city.isNullOrBlank() -> {
                val query = city.trim()
                val matches = XiaomiWeatherParser.locations(service.search(query))
                matches.firstOrNull { it.name == query }
                    ?: matches.firstOrNull { it.name.removeSuffix("市") == query.removeSuffix("市") }
                    ?: matches.firstOrNull()
                    ?: throw WeatherQueryException("未找到“$query”，请尝试完整城市或区县名称")
            }
            isProviderKey(adcode) -> XiaomiWeatherParser.locations(service.cityInfo(adcode!!))
                .firstOrNull { it.key == adcode }
                ?: throw WeatherQueryException("该城市暂不可用，请重新选择城市")
            // A missing location or a legacy administrative code uses the explicit campus city.
            else -> DEFAULT_LOCATION
        }
        return load(location, isLocated = false)
    }

    override suspend fun getWeatherAt(latitude: Double, longitude: Double): WeatherResponse {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) throw WeatherQueryException("定位坐标无效，请重新定位")
        val location = XiaomiWeatherParser.locations(service.locate(latitude, longitude)).firstOrNull()
            ?: throw WeatherQueryException("暂时无法识别当前位置，请手动选择城市")
        return load(location.copy(latitude = latitude, longitude = longitude), isLocated = true)
    }

    private suspend fun load(location: XiaomiWeatherLocation, isLocated: Boolean): WeatherResponse = coroutineScope {
        val advice = auxService?.let { async { XiaomiLifeAdvice.load(it, location, isLocated) } }
        val weather = XiaomiWeatherParser.parse(
            service.weather(location.latitude, location.longitude, location.key, isLocated),
            location
        )
        weather.copy(lifeIndices = advice?.await())
    }

    companion object {
        private val PROVIDER_KEY = Regex("[A-Za-z][A-Za-z0-9_-]*:[^\\s]+")
        internal fun isProviderKey(key: String?): Boolean = key?.matches(PROVIDER_KEY) == true

        private val DEFAULT_LOCATION = XiaomiWeatherLocation(
            key = "weathercn:101220109", name = "蜀山区", affiliation = "合肥市, 安徽, 中国",
            latitude = 31.838, longitude = 117.231
        )
    }
}
