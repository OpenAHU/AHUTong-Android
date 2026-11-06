package com.ahu.ahutong.data.weather

import com.ahu.ahutong.data.dao.AHUCache
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Last successful home weather; location lookup is deferred until the cache needs refreshing. */
internal class WeatherCache(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val gson = Gson()
    private val refreshMutex = Mutex()

    private data class Snapshot(val savedAt: Long, val weather: WeatherResponse)

    fun peek(): WeatherResponse? = snapshot()?.weather

    suspend fun getOrRefresh(request: suspend () -> WeatherResponse): WeatherResponse = refreshMutex.withLock {
        val cached = snapshot()
        if (cached != null && age(cached) < FRESH_MS) return@withLock cached.weather
        try {
            val weather = request()
            // A storage failure must not turn a successful network request into an error.
            runCatching { write(gson.toJson(Snapshot(now(), weather))) }
            weather
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshot()?.weather ?: throw error
        }
    }

    private fun age(snapshot: Snapshot): Long = now() - snapshot.savedAt

    private fun snapshot(): Snapshot? = runCatching {
        gson.fromJson(read(), Snapshot::class.java)?.takeIf {
            // Gson can populate null into non-null Kotlin properties when a cache is damaged.
            @Suppress("SENSELESS_COMPARISON")
            it.weather != null && age(it) in 0L..MAX_STALE_MS
        }
    }.getOrNull()

    companion object {
        internal const val FRESH_MS = 15 * 60 * 1000L
        internal const val MAX_STALE_MS = 24 * 60 * 60 * 1000L

        val home by lazy {
            WeatherCache(AHUCache::getHomeWeatherSnapshot, AHUCache::saveHomeWeatherSnapshot)
        }
    }
}
