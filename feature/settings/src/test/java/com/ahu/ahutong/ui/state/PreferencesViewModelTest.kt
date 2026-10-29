package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.UserNotice
import com.ahu.ahutong.core.common.UserNoticeHolder
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.core.storage.StartupThemePreferences
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.DEFAULT_THEME_COLOR
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.repository.RepositorySourceHealth
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.testing.FakePersonalizationSettings
import com.ahu.ahutong.testing.FakeCourseReminderControl
import com.ahu.ahutong.testing.FakeBehaviorRecorder
import com.ahu.ahutong.testing.FakeRepositoryIndex
import com.ahu.ahutong.testing.FakeSettingsStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 设置 ViewModel 的契约测试：两个协作方都是 fake，因此不需要设备、不碰 DataStore、
 * 也不会碰到端侧模型。这是 P3 对每个 feature 的要求——ViewModel 只依赖接口，
 * 于是它的行为可以被回归，而不是靠真机点一遍。
 *
 * 用 UnconfinedTestDispatcher：ViewModel 的收集与写入都是立即完成的，断言可以直接读结果。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) = Unit
        })
        Dispatchers.resetMain()
    }

    private val reminders = FakeCourseReminderControl()
    private val behavior = FakeBehaviorRecorder()

    private fun viewModel(
        store: SettingsStore = FakeSettingsStore(),
        personalization: FakePersonalizationSettings = FakePersonalizationSettings(),
        repository: FakeRepositoryIndex = FakeRepositoryIndex()
    ) = PreferencesViewModel(store, personalization, reminders, repository, behavior)

    @Test
    fun `material floating bottom bar is disabled by default and persists independently of theme`() {
        val store = FakeSettingsStore()
        val subject = viewModel(store)
        assertFalse(subject.materialFloatingBottomBar.value)

        subject.setMaterialFloatingBottomBar(true)
        assertTrue(store.materialFloatingBottomBar.value)
        assertTrue(subject.materialFloatingBottomBar.value)

        subject.setAppUiTheme(AppUiTheme.MIUIX)
        subject.setAppUiTheme(AppUiTheme.MATERIAL)
        assertTrue(viewModel(store).materialFloatingBottomBar.value)

        subject.setMaterialFloatingBottomBar(false)
        assertFalse(store.materialFloatingBottomBar.value)
        assertFalse(viewModel(store).materialFloatingBottomBar.value)
    }

    @Test
    fun `enabling waits for storage before scheduling and a following disable wins`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeSettingsStore()
        val writeGate = CompletableDeferred<Unit>()
        val delayedStore = object : SettingsStore by store {
            override suspend fun setCourseReminderEnabled(value: Boolean) {
                if (value) writeGate.await()
                store.setCourseReminderEnabled(value)
            }
        }
        val subject = viewModel(delayedStore)
        subject.setCourseReminderEnabled(true)
        runCurrent()
        assertFalse(store.courseReminderEnabled.value)
        assertEquals(0, reminders.rescheduleCount)

        subject.setCourseReminderEnabled(false)
        runCurrent()
        assertEquals(0, reminders.cancelCount)
        writeGate.complete(Unit)
        runCurrent()
        assertFalse(store.courseReminderEnabled.value)
        assertEquals(1, reminders.rescheduleCount)
        assertEquals(1, reminders.cancelCount)
    }

    @Test
    fun `granting exact alarm access refreshes state and reschedules only once`() {
        reminders.exactAlarmAllowed = false
        val subject = viewModel()
        assertFalse(subject.courseReminderExactAlarmAllowed.value)
        subject.openCourseReminderExactAlarmSettings()
        assertEquals(1, reminders.openExactAlarmSettingsCount)
        reminders.exactAlarmAllowed = true
        subject.refreshCourseReminderAlarmAccess()
        subject.refreshCourseReminderAlarmAccess()
        assertTrue(subject.courseReminderExactAlarmAllowed.value)
        assertEquals(1, reminders.rescheduleCount)
    }

    @Test
    fun `unknown or testing primary results never enable a saved fallback`() {
        val store = FakeSettingsStore().apply {
            repositoryAccelerationSource.value = "muxyang-fallback"
        }
        val repository = FakeRepositoryIndex()
        val subject = viewModel(store, repository = repository)

        assertEquals("jsdelivr", subject.repositoryAccelerationSource.value)
        repository.accelerationSourceHealth.value = repository.accelerationSources
            .filterNot { it.isFallback }
            .associate { it.id to RepositorySourceHealth.UNAVAILABLE }
            .plus("direct" to RepositorySourceHealth.TESTING)
        assertEquals("jsdelivr", subject.repositoryAccelerationSource.value)

        repository.accelerationSourceHealth.value = repository.accelerationSourceHealth.value
            .minus("direct")
        assertEquals("jsdelivr", subject.repositoryAccelerationSource.value)
        assertEquals("muxyang-fallback", store.repositoryAccelerationSource.value)
    }

    @Test
    fun `all failed sources select fallback without replacing the preferred primary source`() {
        val store = FakeSettingsStore().apply {
            repositoryAccelerationSource.value = "ghfast"
        }
        val repository = FakeRepositoryIndex()
        val subject = viewModel(store, repository = repository)
        val failed = repository.accelerationSources.filterNot { it.isFallback }
            .associate { it.id to RepositorySourceHealth.UNAVAILABLE }

        repository.accelerationSourceHealth.value = failed
            .plus("ghfast" to RepositorySourceHealth.AVAILABLE)
        assertEquals("ghfast", subject.repositoryAccelerationSource.value)

        repository.accelerationSourceHealth.value = failed
        assertEquals("muxyang-fallback", subject.repositoryAccelerationSource.value)
        assertEquals("ghfast", store.repositoryAccelerationSource.value)
        subject.setRepositoryAccelerationSource("muxyang-fallback")
        assertEquals("ghfast", store.repositoryAccelerationSource.value)
        assertTrue(behavior.reports.isEmpty())

        repository.accelerationSourceHealth.value = failed
            .plus("ghfast" to RepositorySourceHealth.AVAILABLE)
        assertEquals("ghfast", subject.repositoryAccelerationSource.value)
        assertEquals(repository.accelerationSourceHealth.value, subject.repositoryAccelerationSourceHealth.value)
    }

    @Test
    fun `a saved removed Moeyy preference selects a current source and allows choosing GHFast`() {
        val store = FakeSettingsStore().apply {
            repositoryAccelerationSource.value = "moeyy"
        }
        val repository = FakeRepositoryIndex()
        val subject = viewModel(store, repository = repository)

        assertFalse(subject.accelerationSources.any { it.id == "moeyy" })
        assertTrue(subject.accelerationSources.any { it.id == "ghfast" && it.name == "GHFast" })
        assertEquals("jsdelivr", subject.repositoryAccelerationSource.value)

        // A legacy health entry must not reintroduce a removed source into the selection.
        repository.accelerationSourceHealth.value = mapOf("moeyy" to RepositorySourceHealth.AVAILABLE)
        assertEquals("jsdelivr", subject.repositoryAccelerationSource.value)
        subject.setRepositoryAccelerationSource("moeyy")
        assertEquals("jsdelivr", subject.repositoryAccelerationSource.value)
        assertTrue(behavior.reports.isEmpty())

        subject.setRepositoryAccelerationSource("ghfast")
        assertEquals("ghfast", subject.repositoryAccelerationSource.value)
        assertEquals("ghfast", store.repositoryAccelerationSource.value)
    }

    @Test
    fun `disabled fallback and unknown source cannot be selected by a stale caller`() {
        val store = FakeSettingsStore()
        val subject = viewModel(store)

        subject.setRepositoryAccelerationSource("muxyang-fallback")
        subject.setRepositoryAccelerationSource("missing")

        assertEquals("jsdelivr", store.repositoryAccelerationSource.value)
        assertTrue(behavior.reports.isEmpty())
    }

    @Test
    fun `source checks stay asynchronous deduplicate and allow selecting a source while pending`() {
        val store = FakeSettingsStore()
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepositoryIndex().apply {
            onSourceTest = { gate.await() }
        }
        val subject = viewModel(store, repository = repository)
        assertTrue(repository.sourceTestRequests.isEmpty())

        subject.testRepositoryAccelerationSources()
        subject.testRepositoryAccelerationSources()
        subject.testRepositoryAccelerationSources(force = true)
        assertEquals(listOf(false), repository.sourceTestRequests)

        subject.setRepositoryAccelerationSource("gh-proxy")
        assertEquals("gh-proxy", store.repositoryAccelerationSource.value)
        assertEquals("gh-proxy", subject.repositoryAccelerationSource.value)

        gate.complete(Unit)
        subject.testRepositoryAccelerationSources(force = true)
        assertEquals(listOf(false, true), repository.sourceTestRequests)
    }

    @Test
    fun `unexpected source check failures preserve the original throwable and allow retry`() {
        val failures = mutableListOf<Throwable>()
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) {
                failures += error
            }
        })
        val failure = IllegalStateException("source probe failed", IllegalArgumentException("cause"))
        val repository = FakeRepositoryIndex().apply {
            onSourceTest = { throw failure }
        }
        val subject = viewModel(repository = repository)

        subject.testRepositoryAccelerationSources(force = true)
        assertSame(failure, failures.single())

        repository.onSourceTest = {}
        subject.testRepositoryAccelerationSources(force = true)
        assertEquals(listOf(true, true), repository.sourceTestRequests)
        assertEquals(1, failures.size)
    }

    @Test
    fun `cancelled source checks do not report an error and allow retry`() {
        val failures = mutableListOf<Throwable>()
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) {
                failures += error
            }
        })
        val repository = FakeRepositoryIndex().apply {
            onSourceTest = { throw CancellationException("source check cancelled") }
        }
        val subject = viewModel(repository = repository)

        subject.testRepositoryAccelerationSources()
        assertTrue(failures.isEmpty())

        repository.onSourceTest = {}
        subject.testRepositoryAccelerationSources(force = true)
        assertEquals(listOf(false, true), repository.sourceTestRequests)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `the startup mirror decides the first frame before DataStore answers`() {
        // 这一例看的是"设置还没回答"的那一帧，所以换成 StandardTestDispatcher：
        // 收集器在 advanceUntilIdle 之前不会运行，第一帧只能来自启动镜像。
        Dispatchers.setMain(StandardTestDispatcher())

        val store = FakeSettingsStore().apply {
            startupTheme = StartupThemePreferences(AppUiTheme.MIUIX, "#123456", AppThemeMode.DARK)
        }

        val viewModel = viewModel(store, FakePersonalizationSettings())

        assertTrue(viewModel.isUiThemePreferenceReady.value)
        assertEquals(AppUiTheme.MIUIX, viewModel.appUiTheme.value)
        assertEquals("#123456", viewModel.themeColor.value)
        assertEquals(AppThemeMode.DARK, viewModel.appThemeMode.value)
    }

    @Test
    fun `without a mirror the screen waits for the store and assumes the default`() {
        Dispatchers.setMain(StandardTestDispatcher())

        val viewModel = viewModel(FakeSettingsStore(), FakePersonalizationSettings())

        assertFalse(viewModel.isUiThemePreferenceReady.value)
        assertEquals(AppUiTheme.RADIANT, viewModel.appUiTheme.value)
        assertNull(viewModel.themeColor.value)
    }

    @Test
    fun `changing the QR code persists it and reports the committed mutation`() {
        val store = FakeSettingsStore()
        val personalization = FakePersonalizationSettings()
        val viewModel = viewModel(store, personalization)

        viewModel.setShowQRCode(true)

        assertTrue(store.showQRCode.value)
        assertEquals(1, behavior.reports.size)
        val report = behavior.reports.single()
        assertEquals(MutationId.HOME_DEFAULT_QR_CHANGED, report.mutationId)
        assertEquals(false, report.oldValue)
        assertEquals(true, report.newValue)
    }

    @Test
    fun `the reported old value follows the state the screen is showing`() {
        val store = FakeSettingsStore().apply { showQRCode.value = true }
        val personalization = FakePersonalizationSettings()
        val viewModel = viewModel(store, personalization)

        viewModel.setShowQRCode(false)

        assertEquals(true, behavior.reports.single().oldValue)
        assertEquals(false, behavior.reports.single().newValue)
    }

    @Test
    fun `turning personalization off dismisses the current suggestion`() {
        val store = FakeSettingsStore()
        val personalization = FakePersonalizationSettings()
        val viewModel = viewModel(store, personalization)

        viewModel.setPersonalizationEnabled(false)

        assertFalse(store.personalizationEnabled.value)
        assertEquals(1, personalization.dismissedCount)
    }

    @Test
    fun `turning predictive prefetch off also clears wifi-only`() {
        val store = FakeSettingsStore().apply {
            predictivePrefetchEnabled.value = true
            wifiOnlyPrefetch.value = true
        }
        val personalization = FakePersonalizationSettings()
        val viewModel = viewModel(store, personalization)

        viewModel.setPredictivePrefetchEnabled(false)

        assertFalse(store.predictivePrefetchEnabled.value)
        assertFalse(store.wifiOnlyPrefetch.value)
        assertEquals(1, personalization.cancelledPrefetchCount)
    }

    @Test
    fun `wifi-only cannot be switched on while predictive prefetch is off`() {
        val store = FakeSettingsStore()
        val viewModel = viewModel(store, FakePersonalizationSettings())

        viewModel.setWifiOnlyPrefetch(true)

        assertFalse(store.wifiOnlyPrefetch.value)
    }

    @Test
    fun `switching to the Miuix theme stores its default colour and reports once`() {
        val store = FakeSettingsStore()
        val personalization = FakePersonalizationSettings()
        val viewModel = viewModel(store, personalization)

        viewModel.setAppUiTheme(AppUiTheme.MIUIX)

        assertEquals(AppUiTheme.MIUIX, viewModel.appUiTheme.value)
        assertEquals(DEFAULT_THEME_COLOR, store.themeColor.value)
        assertEquals(1, behavior.reports.size)
        assertEquals("UI_STYLE_CHANGED", behavior.reports.single().coarseValueBucket)
    }

}
