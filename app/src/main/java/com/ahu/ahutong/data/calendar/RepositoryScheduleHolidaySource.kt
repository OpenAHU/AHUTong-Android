package com.ahu.ahutong.data.calendar

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.ahu.ahutong.appwidget.WidgetUpdateScheduler
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import com.ahu.ahutong.data.toAhuError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

@Singleton
class RepositoryScheduleHolidaySource @Inject constructor(
    @ApplicationContext private val context: Context
) : ScheduleHolidaySource {
    private val client = AhuHttp.plain(callTimeoutSeconds = 8).build()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calendar = NetworkHolidayCalendar(::isOnline, ::fetchYear)
    override val holidays = calendar.holidays

    init {
        // 旧版本曾落盘日历；升级后清除，避免离线继续使用。
        context.getSharedPreferences("schedule_holidays", Context.MODE_PRIVATE).edit().clear().apply()
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = checkConnectivity()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                checkConnectivity()
        })
        scope.launch { holidays.collect { WidgetUpdateScheduler.renderCached(context) } }
        scope.launch {
            while (isActive) {
                delay(30_000L)
                calendar.expire()
            }
        }
    }

    override suspend fun load(years: Set<Int>, refresh: Boolean) = withContext(Dispatchers.IO) {
        calendar.load(years, refresh)
    }

    private fun checkConnectivity() {
        if (!isOnline()) calendar.invalidate()
    }

    private fun isOnline(): Boolean {
        val manager = connectivity ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun fetchYear(year: Int): AhuResult<Map<LocalDate, ScheduleHoliday>> {
        var error: AhuError = AhuError.Network
        for (baseUrl in DATA_URLS) {
            try {
                val request = Request.Builder().url("$baseUrl/$year.json").build()
                val json = awaitHolidayCalendarJson(client.newCall(request), MAX_DATA_BYTES)
                val parsed = try {
                    parseHolidayCalendar(json, year)
                } catch (invalid: Exception) {
                    error = AhuError.ProtocolChanged(invalid.message.orEmpty())
                    continue
                }
                return AhuResult.Success(parsed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                error = failed.toAhuError()
            }
        }
        return AhuResult.Failure(error)
    }

    private companion object {
        const val MAX_DATA_BYTES = 128 * 1024
        val DATA_URLS = listOf(
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master",
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master"
        )
    }
}

/** 等完整正文后才完成，确保外部取消在服务器慢传正文时仍能取消 Call。 */
internal suspend fun awaitHolidayCalendarJson(call: Call, maxBytes: Int): String =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.use {
                        check(it.isSuccessful)
                        val body = checkNotNull(it.body)
                        require(body.contentLength() <= maxBytes)
                        val source = body.source()
                        require(!source.request(maxBytes.toLong() + 1))
                        source.readUtf8()
                    }
                    continuation.resumeWith(Result.success(json))
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
