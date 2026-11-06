package com.ahu.ahutong.data.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WeatherCacheTest {
    private var stored: String? = null
    private var time = 1_000_000L
    private fun cache() = WeatherCache({ stored }, { stored = it }, { time })
    private val sunny = WeatherResponse(city = "合肥", weather = "晴", temperature = 20.0,
        hourlyForecast = listOf(HourlyForecast(temperature = 21.0)))

    @Test fun `fresh result survives recreation and avoids repeated location and network work`() = runTest {
        val cache = cache()
        assertNull(cache.peek())
        cache.getOrRefresh { sunny }
        assertEquals(sunny, cache().peek())
        assertEquals(sunny, cache().getOrRefresh { error("Fresh weather must not request again") })
    }

    @Test fun `expired weather stays visible during refresh and offline failure`() = runTest {
        val cache = cache()
        cache.getOrRefresh { sunny }
        time += WeatherCache.FRESH_MS
        assertEquals(sunny, cache.peek())
        assertEquals(sunny, cache.getOrRefresh { throw IOException("offline") })
        val rainy = sunny.copy(weather = "雨")
        assertEquals(rainy, cache.getOrRefresh { rainy })
        assertEquals(rainy, cache().peek())
    }

    @Test fun `simultaneous cards share a single request`() = runTest {
        val cache = cache()
        var requests = 0
        val results = List(4) { async {
            cache.getOrRefresh { requests++; yield(); sunny }
        } }.awaitAll()
        assertEquals(1, requests)
        assertTrue(results.all { it == sunny })
    }

    @Test fun `corrupt future and day old caches are discarded`() = runTest {
        stored = "{broken"
        assertNull(cache().peek())
        stored = "{}"
        assertNull(cache().peek())
        cache().getOrRefresh { sunny }
        time--
        assertNull(cache().peek())
        time += WeatherCache.MAX_STALE_MS + 2
        assertNull(cache().peek())
        try {
            cache().getOrRefresh { throw IOException("offline") }
            fail("An unusable cache must not hide a request failure")
        } catch (_: IOException) { }
    }

    @Test fun `cancellation propagates without persisting a partial result`() = runTest {
        try {
            cache().getOrRefresh { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertNull(stored)
    }
}
