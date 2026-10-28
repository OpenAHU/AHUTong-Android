package com.ahu.ahutong.ui.screen.main.home

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.ui.state.WeatherHomeConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAcademicReturnWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 60_000)
    fun navigationRetainsTheTimetableAndRefreshesOnlyWhenDataChanges() {
        val fixture = Fixture()
        val savedWeather = WeatherHomeConfig.fromCache()
        savedWeather.copy(showOnHome = true).saveToCache()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { fixture.install(it) }
                fixture.advance()
                assertEquals(3, fixture.seenWeeks.last())
                assertTrue(fixture.weatherVisible.last())
                instrumentation.runOnMainSync { fixture.homeActive.value = false }
                fixture.advance()
                assertTrue("The outgoing home must retain weather and its header height", fixture.weatherVisible.last())
                instrumentation.runOnMainSync { fixture.nav.navigate("other") }
                fixture.advance()
                assertTrue("The old home composition must be disposed before returning", fixture.homeDisposals.get() > 0)
                instrumentation.runOnMainSync {
                    fixture.nextLoad = CompletableDeferred()
                    fixture.seenWeeks.clear()
                    fixture.homeActive.value = true
                    fixture.nav.popBackStack()
                }
                fixture.advance()
                assertTrue("Home must render its timetable on the first return composition", fixture.seenWeeks.isNotEmpty())
                assertFalse("Returning home must not first render an empty timetable", fixture.seenWeeks.contains(null))
                assertEquals("An unchanged timetable must not be reloaded", 1, fixture.loadCount.get())
                instrumentation.runOnMainSync { fixture.nav.navigate("other") }
                fixture.advance()
                assertTrue("The old home composition must be disposed before returning", fixture.homeDisposals.get() > 0)
                instrumentation.runOnMainSync {
                    fixture.nextLoad = CompletableDeferred()
                    fixture.revision.value++
                    fixture.seenWeeks.clear()
                    fixture.nav.popBackStack()
                }
                fixture.advance()
                assertEquals(2, fixture.loadCount.get())
                assertFalse("Refreshing changed data must not hide the existing timetable", fixture.seenWeeks.contains(null))
                assertEquals(3, fixture.seenWeeks.last())
                instrumentation.runOnMainSync { fixture.nextLoad.complete(ScheduleConfigBean().apply { week = 4 }) }
                fixture.advance()
                assertEquals(4, fixture.seenWeeks.last())
            } finally {
                savedWeather.saveToCache()
                scenario.onActivity { fixture.dispose() }
            }
        }
    }

    private inner class Fixture {
        val seenWeeks = mutableListOf<Int?>()
        val weatherVisible = mutableListOf<Boolean>()
        val homeActive = mutableStateOf(true)
        val loadCount = AtomicInteger()
        val homeDisposals = AtomicInteger()
        val revision = mutableStateOf(1)
        var nextLoad = CompletableDeferred(ScheduleConfigBean().apply { week = 3 })
        lateinit var nav: NavHostController
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(AndroidUiDispatcher.Main + clock + Job())
        private val recomposer = Recomposer(scope.coroutineContext)
        private var view: ComposeView? = null
        private var frameTime = 0L

        fun install(activity: MainActivity) {
            view = ComposeView(activity).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    MaterialTheme {
                        val state = remember { HomeAcademicState() }
                        nav = rememberNavController()
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                val weather = rememberHomeWeatherConfig(null, homeActive.value)
                                val showWeather = weather.showOnHome
                                SideEffect { weatherVisible.add(showWeather) }
                                DisposableEffect(Unit) { onDispose { homeDisposals.incrementAndGet() } }
                                val config by rememberHomeAcademicValue(state.undergraduate, revision.value) {
                                    loadCount.incrementAndGet()
                                    nextLoad.await()
                                }
                                SideEffect { seenWeeks.add(config?.week) }
                                Box(Modifier.fillMaxSize()) { Text(config?.week?.let { "课表：第${it}周" } ?: "无课表") }
                            }
                            composable("other") { Box(Modifier.fillMaxSize()) { Text("其他页面") } }
                        }
                    }
                }
            }
            activity.setContentView(view)
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
        }

        fun advance() {
            repeat(75) {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    Snapshot.sendApplyNotifications()
                    frameTime += 16_666_667L
                    clock.sendFrame(frameTime)
                }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(80)
        }

        fun dispose() {
            view?.disposeComposition()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
