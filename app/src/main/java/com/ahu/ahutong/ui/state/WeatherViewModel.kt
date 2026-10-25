package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.UserNoticeHolder
import kotlinx.coroutines.CancellationException

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.weather.WeatherApi
import com.ahu.ahutong.data.weather.WeatherResponse
import com.ahu.ahutong.data.weather.WeatherQueryException
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import javax.inject.Inject
import com.ahu.ahutong.data.session.SessionStore

enum class WeatherHomeMode(val cacheValue: String) {
    Detailed("detailed"),
    Compact("compact");

    companion object {
        fun fromCacheValue(value: String?): WeatherHomeMode {
            return values().firstOrNull { it.cacheValue == value } ?: Detailed
        }
    }
}

data class WeatherHomeConfig(
    val showOnHome: Boolean = false,
    val mode: WeatherHomeMode = WeatherHomeMode.Detailed,
    val showTemp: Boolean = true,
    val showWeather: Boolean = true,
    val showAqi: Boolean = true,
    val showLocation: Boolean = true,
) {
    fun saveToCache() {
        AHUCache.saveWeatherShowOnHome(showOnHome)
        AHUCache.saveWeatherHomeMode(mode.cacheValue)
        AHUCache.saveWeatherHomeShowTemp(showTemp)
        AHUCache.saveWeatherHomeShowWeather(showWeather)
        AHUCache.saveWeatherHomeShowAqi(showAqi)
        AHUCache.saveWeatherHomeShowLocation(showLocation)
        cachedConfig = CachedWeatherHomeConfig(SessionStore.currentUser()?.xh, this)
    }

    companion object {
        private data class CachedWeatherHomeConfig(
            val userId: String?,
            val config: WeatherHomeConfig
        )

        @Volatile
        private var cachedConfig: CachedWeatherHomeConfig? = null

        fun fromCache(): WeatherHomeConfig {
            val userId = SessionStore.currentUser()?.xh
            cachedConfig
                ?.takeIf { it.userId == userId }
                ?.let { return it.config }
            return synchronized(this) {
                cachedConfig
                    ?.takeIf { it.userId == userId }
                    ?.config
                    ?: WeatherHomeConfig(
                        showOnHome = AHUCache.getWeatherShowOnHome(),
                        mode = WeatherHomeMode.fromCacheValue(AHUCache.getWeatherHomeMode()),
                        showTemp = AHUCache.getWeatherHomeShowTemp(),
                        showWeather = AHUCache.getWeatherHomeShowWeather(),
                        showAqi = AHUCache.getWeatherHomeShowAqi(),
                        showLocation = AHUCache.getWeatherHomeShowLocation(),
                    ).also { config ->
                        cachedConfig = CachedWeatherHomeConfig(userId, config)
                    }
            }
        }
    }
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    var weather by mutableStateOf<WeatherResponse?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var lastCity by mutableStateOf<String?>(null)
        private set

    var lastAdcode by mutableStateOf<String?>(null)
        private set

    var lastLocationName by mutableStateOf<String?>(null)
        private set

    var homeConfig by mutableStateOf(WeatherHomeConfig())
        private set

    private var lastCoordinates: Pair<Double, Double>? = null
    private var locationStreet: String? = null

    init {
        homeConfig = WeatherHomeConfig.fromCache()
    }

    fun fetchWeather(city: String? = null) {
        if (isLoading) return
        lastCity = city?.trim()?.takeIf { it.isNotEmpty() }
        lastAdcode = null
        lastCoordinates = null
        locationStreet = null
        loadWeather { WeatherApi.API.getWeather(city = lastCity) }
    }

    fun refresh() {
        if (isLoading) return
        val coordinates = lastCoordinates
        if (coordinates != null) {
            loadWeather { WeatherApi.API.getWeatherAt(coordinates.first, coordinates.second) }
        } else if (lastAdcode != null) {
            fetchWeatherByAdcode(lastAdcode!!)
        } else {
            fetchWeather(lastCity)
        }
    }

    private fun fetchWeatherByAdcode(adcode: String) {
        if (isLoading) return
        lastAdcode = adcode
        lastCity = null
        lastCoordinates = null
        loadWeather { WeatherApi.API.getWeather(adcode = adcode) }
    }

    /**
     * 系统近期位置或限时单次定位匹配区县天气，不启动持续定位。
     * 街道名是限时可选信息，失败不影响天气结果。
     * 无新鲜位置时使用上次成功的城市；首次无定位时显示校区所在的蜀山区。
     */
    fun fetchWeatherByLocation(context: Context) {
        if (isLoading) return
        loadWeather {
            val location = withContext(Dispatchers.IO) { getLocation(context) }
            lastCity = null
            locationStreet = null
            // 旧接口的行政区码不能当作小米天气 locationKey。
            lastAdcode = AHUCache.getWeatherAdcode()?.takeIf { ':' in it }
            lastCoordinates = location?.let { it.latitude to it.longitude }
            if (location != null) {
                val result = WeatherApi.API.getWeatherAt(location.latitude, location.longitude)
                locationStreet = withContext(Dispatchers.IO) { getStreetName(context, location) }
                result
            } else {
                WeatherApi.API.getWeather(adcode = lastAdcode)
            }
        }
    }

    private fun loadWeather(request: suspend () -> WeatherResponse) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val result = request()
                weather = result
                val district = listOfNotNull(result.district, result.city, result.province)
                    .firstOrNull { it.isNotBlank() }
                lastLocationName = listOfNotNull(district, locationStreet)
                    .filter { it.isNotBlank() }.distinct().joinToString(" ")
                result.adcode?.takeIf { ':' in it }?.let {
                    lastAdcode = it
                    lastCity = null
                    AHUCache.saveWeatherAdcode(it)
                }
                reportReady()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("Weather", "Failed to fetch weather", e)
                if (e !is WeatherQueryException) UserNoticeHolder.reportUnexpected(e)
                // 保留已加载内容，并始终提供本页重试入口。
                errorMessage = if (e is WeatherQueryException) e.message
                    else "天气暂时无法更新，请检查网络后重试"
                reportError()
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun getLocation(context: Context): Location? {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val now = SystemClock.elapsedRealtimeNanos()
        val recent = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { now - it.elapsedRealtimeNanos in 0L..600_000_000_000L }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (recent != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return recent
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it != LocationManager.GPS_PROVIDER || hasFineLocation }
            .firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        return withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private suspend fun getStreetName(context: Context, location: Location): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !Geocoder.isPresent()) return null
        if (!location.hasAccuracy() || location.accuracy > 200f || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) return null
        return withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    Geocoder(context, Locale.SIMPLIFIED_CHINESE).getFromLocation(
                        location.latitude, location.longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                val address = addresses.firstOrNull()
                                val street = address?.thoroughfare?.takeIf { it.isNotBlank() }
                                if (continuation.isActive) continuation.resume(street)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }
                    )
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    fun updateHomeConfig(config: WeatherHomeConfig) {
        val previous = homeConfig
        if (previous == config) return
        config.saveToCache()
        val committed = WeatherHomeConfig.fromCache()
        homeConfig = committed
        if (committed != config) return
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.WEATHER_HOME_CONFIG_CHANGED,
            previous,
            committed,
            coarseValueBucket = "CONFIG_CHANGED"
        )
    }

    private fun reportReady() {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.WEATHER,
            ContentStateBucket.READY,
            freshnessBucket = 0,
            resultCount = ResultCountBucket.ONE_TO_FIVE
        )
    }

    private fun reportError() {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.WEATHER,
            ContentStateBucket.ERROR,
            freshnessBucket = 7,
            resultCount = ResultCountBucket.ZERO,
            errorType = ErrorTypeBucket.NETWORK
        )
    }

    val locationName: String
        get() {
            lastLocationName?.let { return it }
            val w = weather ?: return ""
            return listOfNotNull(w.district, w.city, w.province)
                .firstOrNull { it.isNotBlank() } ?: ""
        }
}
