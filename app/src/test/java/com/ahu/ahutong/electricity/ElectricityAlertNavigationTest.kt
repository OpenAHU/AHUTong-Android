package com.ahu.ahutong.electricity

import androidx.lifecycle.ViewModelStore
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.recharge.ElectricityDailyUsage
import com.ahu.ahutong.data.recharge.ElectricityUsageSnapshot
import com.ahu.ahutong.data.recharge.ElectricityUsageSource
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.ui.state.ElectricityAlertViewModel
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ElectricityAlertNavigationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val models = ViewModelStore()
    private val room = ElectricityAlertRoom(RoomSelectionInfo(
        CampusDataItem("校区", "1"), CampusDataItem("楼栋", "1"),
        CampusDataItem("楼层", "1"), CampusDataItem("101", "101"), ElectricityController.C
    ))
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AhuSessionState.markAuthenticated()
    }
    @After fun cleanup() {
        models.clear()
        AhuSessionState.markAnonymous()
        Dispatchers.resetMain()
    }

    @Test fun `disposing navigation does not cancel the only daily electricity check`() = runBlocking {
        val store = LocalElectricityAlertStore(ElectricityAlertFiles(temporary.root))
        store.activateProfile("user")
        store.saveRoom(room)
        store.setEnabled(true)
        val source = DeferredUsage()
        val identity = object : SessionIdentity {
            override fun isLoggedIn() = true
            override fun currentUser() = User("name", "user")
        }
        val model = ElectricityAlertViewModel(store, source, identity)
        models.put("alert", model)
        val navigation = launch { model.resumeSession(true); awaitCancellation() }
        withTimeout(5_000) { source.started.await() }
        navigation.cancel()
        model.resumeSession(true)
        source.complete.complete(Unit)
        val pending = withTimeout(5_000) { model.pending.first { it.isNotEmpty() } }
        assertEquals(listOf(room), pending)
        assertEquals(1, source.calls)
        val promptHost = launch(start = CoroutineStart.UNDISPATCHED) {
            model.showPending()
            awaitCancellation()
        }
        promptHost.cancel()
        assertEquals(listOf(room), withTimeout(5_000) { model.presented.first { it.isNotEmpty() } })
        assertEquals(LocalDate.now().toString(), store.files.marker("user").lastPromptDay)
    }

    @Test fun `settings persist independently for multiple accounts`() = runBlocking {
        val store = LocalElectricityAlertStore(ElectricityAlertFiles(temporary.root))
        store.activateProfile("first")
        store.saveRoom(room)
        store.setEnabled(true)
        store.activateProfile("second")
        assertEquals(false, store.configuration.value.enabled)
        assertEquals(emptyList(), store.configuration.value.rooms)
        store.saveRoom(room.copy(thresholdDays = 7))
        store.activateProfile("first")
        assertEquals(true, store.configuration.value.enabled)
        assertEquals(listOf(room), store.configuration.value.rooms)
        val restarted = LocalElectricityAlertStore(ElectricityAlertFiles(temporary.root))
        restarted.activateProfile("second")
        assertEquals(7, restarted.configuration.value.rooms.single().thresholdDays)
        assertEquals(false, restarted.configuration.value.enabled)
    }

    private class DeferredUsage : ElectricityUsageSource {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        var calls = 0
        override suspend fun snapshot(controller: ElectricityController, selection: RoomSelectionInfo, asOf: LocalDate):
            AhuResult<ElectricityUsageSnapshot> {
            calls++
            started.complete(Unit)
            complete.await()
            return AhuResult.Success(ElectricityUsageSnapshot(asOf, 20.0,
                (1L..30L).map { ElectricityDailyUsage(asOf.minusDays(it), 10.0) },
                asOf.minusDays(30), asOf.minusDays(1)))
        }
    }
}
