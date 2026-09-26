package com.ahu.ahutong.electricity

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityDailyUsage
import com.ahu.ahutong.data.recharge.ElectricityUsageSnapshot
import com.ahu.ahutong.data.recharge.ElectricityUsageSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ElectricityAlertCheckerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val today = LocalDate.of(2026, 9, 26)
    private val room = ElectricityAlertRoom(RoomSelectionInfo(
        campus = CampusDataItem("磬苑校区", "1"),
        building = CampusDataItem("一栋", "1"), floor = CampusDataItem("一层", "1"),
        room = CampusDataItem("101", "101"), controller = ElectricityController.C
    ))
    private val enabled = ElectricityAlertConfiguration(true, listOf(room))
    private val source = FakeUsage()

    @Test fun `disabled alert makes no requests and missing marker initializes on enabled login`() = runTest {
        val checker = checker()
        assertTrue(checker.check("user", enabled.copy(enabled = false), today).isEmpty())
        assertEquals(0, source.calls)
        assertEquals(listOf(room), checker.check("user", enabled, today))
        assertEquals(1, source.calls)
    }

    @Test fun `query and prompt limits survive new checker and file instances`() = runTest {
        val checker = checker()
        assertEquals(listOf(room), checker.check("user", enabled, today))
        val restarted = checker()
        assertEquals(listOf(room), restarted.check("user", enabled, today))
        assertEquals(1, source.calls)
        assertTrue(restarted.claimPrompt("user", today))
        assertFalse(checker().claimPrompt("user", today))
        assertTrue(checker().check("user", enabled, today).isEmpty())
        assertEquals(listOf(room), checker().check("user", enabled, today.plusDays(1)))
        assertEquals(2, source.calls)
    }

    @Test fun `network failure or cancellation consumes todays request slot without a false alert`() = runTest {
        source.failure = true
        assertTrue(checker().check("user", enabled, today).isEmpty())
        source.failure = false
        assertTrue(checker().check("user", enabled, today).isEmpty())
        assertEquals(1, source.calls)
        source.cancel = true
        try { checker().check("user", enabled, today.plusDays(1)) } catch (_: CancellationException) { }
        source.cancel = false
        assertTrue(checker().check("user", enabled, today.plusDays(1)).isEmpty())
        assertEquals(2, source.calls)
    }

    @Test fun `recharged balance reschedules future confirmation and does not query early`() = runTest {
        val checker = checker()
        checker.check("user", enabled, today)
        source.balance = 100.0
        assertTrue(checker.check("user", enabled, today.plusDays(1)).isEmpty())
        assertTrue(checker.check("user", enabled, today.plusDays(2)).isEmpty())
        assertEquals(2, source.calls)
        val marker = files().marker("user").rooms.getValue(room.key)
        assertEquals(today.plusDays(7).toString(), marker.nextCheckDay)
        assertEquals(today.plusDays(11).toString(), marker.zeroDay)
    }

    @Test fun `each room has independent threshold and account markers are isolated`() = runTest {
        val second = room.copy(selection = room.selection.copy(room = CampusDataItem("102", "102")), thresholdDays = 1)
        val config = enabled.copy(rooms = listOf(room, second))
        assertEquals(listOf(room), checker().check("first", config, today))
        assertEquals(2, source.calls)
        assertTrue(checker().claimPrompt("first", today))
        assertEquals(listOf(room), checker().check("second", config, today))
        assertEquals(4, source.calls)
        assertTrue(checker().claimPrompt("second", today))
    }

    @Test fun `changing threshold reevaluates todays sample without another request`() = runTest {
        source.balance = 35.0
        assertTrue(checker().check("user", enabled, today).isEmpty())
        val changed = room.copy(thresholdDays = 4)
        assertEquals(listOf(changed), checker().check("user", enabled.copy(rooms = listOf(changed)), today))
        assertEquals(1, source.calls)
    }

    @Test fun `removing and readding a room after checking another room preserves its daily limit`() = runTest {
        val other = room.copy(selection = room.selection.copy(room = CampusDataItem("102", "102")))
        checker().check("user", enabled, today)
        checker().check("user", enabled.copy(rooms = listOf(other)), today)
        checker().check("user", enabled.copy(rooms = listOf(room, other)), today)
        assertEquals(2, source.calls)
        checker().check("user", enabled, today.plusDays(1))
        assertEquals(3, source.calls)
    }

    @Test fun `a late response after account switch cannot save a forecast or produce an alert`() = runTest {
        var current = true
        source.afterRequest = { current = false }
        val checker = ElectricityAlertChecker(files(), source) { current }
        assertTrue(checker.check("user", enabled, today).isEmpty())
        assertEquals(null, files().marker("user").rooms.getValue(room.key).sampleDay)
        assertFalse(checker.claimPrompt("user", today))
    }

    @Test fun `legacy forecasts upgrade without bypassing an already consumed daily request`() = runTest {
        val old = ElectricityRoomForecast(3, today.toString(), sampleDay = today.toString(),
            remainingKwh = 20.0, dailyKwh = 10.0, nextCheckDay = today.plusDays(90).toString())
        files().saveMarker("user", ElectricityAlertMarker(mapOf(room.key to old)))
        assertEquals(listOf(room), checker().check("user", enabled, today))
        assertEquals(0, source.calls)
        assertEquals(listOf(room), checker().check("user", enabled, today.plusDays(1)))
        assertEquals(1, source.calls)
        val upgraded = files().marker("user").rooms.getValue(room.key)
        assertEquals(2, upgraded.algorithmVersion)
        assertEquals(30, upgraded.riskKwhByDays?.size)
    }

    @Test fun `a new sample uses cumulative risk rather than the central daily rate`() = runTest {
        val risk = (1..30).map { it * 5.0 }
        val saved = ElectricityRoomForecast(3, today.toString(), sampleDay = today.toString(),
            remainingKwh = 12.0, dailyKwh = 2.0, nextCheckDay = today.toString(),
            algorithmVersion = 2, riskKwhByDays = risk)
        files().saveMarker("user", ElectricityAlertMarker(mapOf(room.key to saved)))
        assertEquals(listOf(room), checker().check("user", enabled, today))
        assertEquals(0, source.calls)
    }

    private fun files() = ElectricityAlertFiles(temporary.root)
    private fun checker() = ElectricityAlertChecker(files(), source) { true }

    private class FakeUsage : ElectricityUsageSource {
        var calls = 0
        var balance = 20.0
        var failure = false
        var cancel = false
        var afterRequest: () -> Unit = {}
        override suspend fun snapshot(controller: ElectricityController, selection: RoomSelectionInfo, asOf: LocalDate):
            AhuResult<ElectricityUsageSnapshot> {
            calls++
            if (cancel) throw CancellationException()
            if (failure) return AhuResult.Failure(AhuError.Unknown("offline"))
            afterRequest()
            return AhuResult.Success(ElectricityUsageSnapshot(asOf, balance,
                (1L..30L).map { ElectricityDailyUsage(asOf.minusDays(it), 10.0) },
                asOf.minusDays(30), asOf.minusDays(1)))
        }
    }
}
