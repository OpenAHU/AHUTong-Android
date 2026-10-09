package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 优先显示缓存，在独立于页面和后台查询预算的作用域内重新验证。 */
internal class NetworkHolidayCalendar(
    private val isOnline: () -> Boolean,
    private val fetch: suspend (Int) -> AhuResult<Map<LocalDate, ScheduleHoliday>>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val cache: HolidayCalendarCache,
    private val refreshScope: CoroutineScope
) {
    private data class CachedYear(val days: Map<LocalDate, ScheduleHoliday>, val checkedAt: Long?)
    private val mutex = Mutex()
    private val stateLock = Any()
    private val generation = AtomicLong()
    private val cachedYears = mutableMapOf<Int, CachedYear>()
    private val restoredYears = mutableSetOf<Int>()
    private val attemptedAt = mutableMapOf<Int, Long>()
    private val failures = mutableMapOf<Int, AhuError>()
    private val state = MutableStateFlow<Map<LocalDate, ScheduleHoliday>>(emptyMap())
    val holidays = state.asStateFlow()

    fun onNetworkLost() { generation.incrementAndGet() }

    suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
        val cached = synchronized(stateLock) {
            years.sorted().forEach { year ->
                if (restoredYears.add(year)) {
                    runCatching { cache.read(year) }.getOrNull()?.let {
                        cachedYears[year] = CachedYear(it.toMap(), null)
                    }
                }
            }
            publish()
            merged(years)
        }
        val updating = refreshScope.async { refreshYears(years, refresh) }
        return if (cached.isNotEmpty()) AhuResult.Success(cached) else updating.await()
    }

    private suspend fun refreshYears(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> =
        mutex.withLock {
            if (!isOnline()) return@withLock cachedOrFailure(years, AhuError.Network)
            var error: AhuError? = null
            for (year in years.sorted()) {
                val now = nowMillis()
                val checkedAt = synchronized(stateLock) { cachedYears[year]?.checkedAt }
                if (!refresh && checkedAt != null && now - checkedAt in 0 until VALID_MILLIS) continue
                val lastAttempt = attemptedAt[year]
                val interval = if (failures.containsKey(year)) FAILURE_RETRY_MILLIS else MIN_REQUEST_MILLIS
                if (lastAttempt != null && now - lastAttempt in 0 until interval) {
                    error = failures[year] ?: error
                    continue
                }
                val requestGeneration = generation.get()
                attemptedAt[year] = now
                val result = try {
                    fetch(year)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AhuResult.Failure(AhuError.Network)
                }
                val accepted = synchronized(stateLock) {
                    if (!isOnline() || generation.get() != requestGeneration) false
                    else {
                        when (result) {
                            is AhuResult.Success -> {
                                val days = result.value.toMap()
                                if (cachedYears[year]?.days != days) {
                                    runCatching { cache.write(year, days) }
                                }
                                cachedYears[year] = CachedYear(days, nowMillis())
                                failures.remove(year)
                            }
                            is AhuResult.Failure -> {
                                failures[year] = result.error
                                error = result.error
                            }
                        }
                        publish()
                        true
                    }
                }
                if (!accepted) return@withLock cachedOrFailure(years, AhuError.Network)
            }
            cachedOrFailure(years, error)
        }

    private fun cachedOrFailure(years: Set<Int>, error: AhuError?) = synchronized(stateLock) {
        val requested = merged(years)
        if (requested.isEmpty() && error != null) AhuResult.Failure(error)
        else AhuResult.Success(requested)
    }

    // StateFlow 按内容比较，相同日历不会触发页面和组件重绘。
    private fun publish() { state.value = merged(cachedYears.keys) }

    private fun merged(years: Set<Int>): Map<LocalDate, ScheduleHoliday> = buildMap {
        years.sorted().forEach { year -> cachedYears[year]?.let { putAll(it.days) } }
    }

    companion object {
        const val VALID_MILLIS = 5 * 60 * 1000L
        const val MIN_REQUEST_MILLIS = 30 * 1000L
        const val FAILURE_RETRY_MILLIS = 60 * 1000L
    }
}
