package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 只共享本次进程里联网取得的结果，不从磁盘或随包数据恢复。 */
internal class NetworkHolidayCalendar(
    private val isOnline: () -> Boolean,
    private val fetch: suspend (Int) -> AhuResult<Map<LocalDate, ScheduleHoliday>>,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class VerifiedYear(val days: Map<LocalDate, ScheduleHoliday>, val checkedAt: Long)
    private val mutex = Mutex()
    private val stateLock = Any()
    private val generation = AtomicLong()
    private val verified = ConcurrentHashMap<Int, VerifiedYear>()
    private val attemptedAt = mutableMapOf<Int, Long>()
    private val failures = mutableMapOf<Int, AhuError>()
    private val state = MutableStateFlow<Map<LocalDate, ScheduleHoliday>>(emptyMap())
    val holidays = state.asStateFlow()

    fun invalidate() = synchronized(stateLock) {
        generation.incrementAndGet()
        verified.clear()
        state.value = emptyMap()
    }

    suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> =
        mutex.withLock {
            if (!isOnline()) {
                invalidate()
                return@withLock AhuResult.Failure(AhuError.Network)
            }
            var error: AhuError? = null
            for (year in years.sorted()) {
                val now = nowMillis()
                val existing = synchronized(stateLock) {
                    verified[year]?.takeIf { now - it.checkedAt in 0 until VALID_MILLIS }
                }
                if (!refresh && existing != null) continue
                val lastAttempt = attemptedAt[year]
                val interval = if (failures.containsKey(year)) FAILURE_RETRY_MILLIS else MIN_REQUEST_MILLIS
                if (lastAttempt != null && now - lastAttempt in 0 until interval) {
                    if (existing == null) synchronized(stateLock) { verified.remove(year) }
                    error = failures[year] ?: error
                    continue
                }
                // 重新验证期间不使用过往安排，失败后也不会回退到它。
                val requestGeneration = synchronized(stateLock) {
                    verified.remove(year)
                    publish()
                    generation.get()
                }
                attemptedAt[year] = now
                val result = try {
                    fetch(year)
                } catch (cancelled: CancellationException) {
                    publish()
                    throw cancelled
                }
                val accepted = synchronized(stateLock) {
                    if (!isOnline() || generation.get() != requestGeneration) {
                        invalidate()
                        false
                    } else {
                        when (result) {
                            is AhuResult.Success -> {
                                verified[year] = VerifiedYear(result.value.toMap(), nowMillis())
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
                if (!accepted) return@withLock AhuResult.Failure(AhuError.Network)
            }
            publish()
            val requested = synchronized(stateLock) { merged(years) }
            if (requested.isEmpty() && error != null) AhuResult.Failure(error)
            else AhuResult.Success(requested)
        }

    fun expire() = publish()

    private fun publish() = synchronized(stateLock) {
        if (!isOnline()) {
            invalidate()
            return@synchronized
        }
        val now = nowMillis()
        verified.entries.removeIf { now - it.value.checkedAt !in 0 until VALID_MILLIS }
        state.value = merged(verified.keys)
    }

    private fun merged(years: Set<Int>): Map<LocalDate, ScheduleHoliday> = buildMap {
        years.sorted().forEach { year -> verified[year]?.let { putAll(it.days) } }
    }

    companion object {
        const val VALID_MILLIS = 5 * 60 * 1000L
        const val MIN_REQUEST_MILLIS = 30 * 1000L
        const val FAILURE_RETRY_MILLIS = 60 * 1000L
    }
}
