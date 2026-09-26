package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.ahu.ahutong.data.recharge.ElectricityRoomDetails
import com.ahu.ahutong.data.recharge.RechargeReceipt
import com.ahu.ahutong.personalization.preset.AppliedPreset
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.preset.PresetSuggestions
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.testing.FakeBehaviorRecorder
import com.ahu.ahutong.testing.FakeElectricityDepositSource
import com.ahu.ahutong.testing.FakeElectricityAlertSettings
import com.ahu.ahutong.testing.FakePaymentKeyboardSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class ElectricityDepositViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `restored room and payment cross typed seams`() = runTest(dispatcher) {
        val selection = selection()
        val source = FakeElectricityDepositSource(
            controller = ElectricityController.C,
            selection = selection,
            optionsResult = AhuResult.Success(listOfNotNull(selection.room)),
            roomResult = AhuResult.Success(room()),
            payResult = AhuResult.Success(RechargeReceipt("order-1"))
        )
        val subject = ElectricityDepositViewModel(
            source,
            FakeBehaviorRecorder(),
            EmptyPresetSuggestions,
            FakePaymentKeyboardSetting(),
            FakeElectricityAlertSettings()
        )
        advanceUntilIdle()

        subject.pay("10", "012345")
        advanceUntilIdle()

        assertEquals(PayState.Succeeded("order-1"), subject.payState.value)
        assertEquals("10", source.payments.single().amount)
        assertEquals("012345", source.payments.single().password)
    }

    @Test
    fun `successful room queries retain multiple recent rooms without payment`() = runTest(dispatcher) {
        val first = selection()
        val second = selection("102")
        val source = source(first)
        val subject = viewModel(source)
        advanceUntilIdle()

        subject.restoreRoom(second)
        advanceUntilIdle()

        assertEquals(listOf(second, first), subject.historyOptions.value.map { it.selection })
        assertTrue(subject.historyOptions.value.none { it.confirmedByPayment })
        assertEquals(second, source.roomQueries.last().second)

        val restored = viewModel(source)
        advanceUntilIdle()
        assertEquals(setOf(first, second), restored.historyOptions.value.map { it.selection }.toSet())

        restored.deleteHistory(restored.historyOptions.value.first { it.selection == first })
        assertEquals(listOf(second), source.depositHistory().map { it.selection })
    }

    @Test
    fun `querying a previously paid room preserves its payment marker without duplicates`() = runTest(dispatcher) {
        val source = source(selection())
        val subject = viewModel(source)
        advanceUntilIdle()
        subject.pay("10", "012345")
        advanceUntilIdle()

        subject.restoreRoom(selection())
        advanceUntilIdle()

        assertEquals(1, subject.historyOptions.value.size)
        assertTrue(subject.historyOptions.value.single().confirmedByPayment)
    }

    @Test
    fun `alert settings default off and support separate room thresholds`() = runTest(dispatcher) {
        val settings = FakeElectricityAlertSettings()
        val subject = viewModel(source(selection()), settings)
        advanceUntilIdle()
        assertFalse(subject.alertConfiguration.value.enabled)

        subject.addCurrentAlertRoom()
        subject.setAlertEnabled(true)
        advanceUntilIdle()
        val firstRoom = subject.alertConfiguration.value.rooms.single()
        assertEquals(3, firstRoom.thresholdDays)

        subject.updateAlertThreshold(firstRoom, 5)
        subject.restoreRoom(selection("102"))
        advanceUntilIdle()
        subject.addCurrentAlertRoom(7)
        advanceUntilIdle()

        assertTrue(subject.alertConfiguration.value.enabled)
        assertEquals(setOf(5, 7), subject.alertConfiguration.value.rooms.map { it.thresholdDays }.toSet())
        assertEquals(2, subject.alertConfiguration.value.rooms.size)

        subject.addCurrentAlertRoom(3)
        subject.updateAlertThreshold(firstRoom, 0)
        advanceUntilIdle()
        assertEquals(setOf(5, 7), subject.alertConfiguration.value.rooms.map { it.thresholdDays }.toSet())

        subject.removeAlertRoom(firstRoom)
        advanceUntilIdle()
        assertEquals("102", subject.alertConfiguration.value.rooms.single().selection.room?.name)
    }

    @Test
    fun `alert recharge prefill switches controller and restores all room fields`() = runTest(dispatcher) {
        val source = source(selection())
        val subject = viewModel(source)
        advanceUntilIdle()
        val controllerA = selection("201").copy(campus = null, controller = ElectricityController.A)

        subject.restoreRoom(controllerA)
        advanceUntilIdle()

        assertEquals(ElectricityController.A, subject.selectedController.value)
        assertEquals(controllerA.building, subject.selectedBuilding.value)
        assertEquals(controllerA.floor, subject.selectedFloor.value)
        assertEquals(controllerA.room, subject.selectedRoom.value)
        assertEquals(ElectricityController.A to controllerA, source.roomQueries.last())
    }

    @Test
    fun `failed alert room restore clears previous details and cannot pay previous room`() = runTest(dispatcher) {
        val source = source(selection())
        val subject = viewModel(source)
        advanceUntilIdle()
        assertEquals(room(), subject.fullRoomDetails.value)

        source.roomResult = AhuResult.Failure(AhuError.Network)
        val nextRoom = selection("102")
        subject.restoreRoom(nextRoom)

        assertNull(subject.fullRoomDetails.value)
        assertNull(subject.roomInfo.value)
        advanceUntilIdle()
        assertEquals(nextRoom.room, subject.selectedRoom.value)
        assertFalse(subject.isLoading.value)

        subject.pay("10", "012345")
        advanceUntilIdle()

        assertEquals(emptyList(), source.payments)
        assertTrue(subject.payState.value is PayState.Failed)
    }

    @Test
    fun `alert settings room selection does not change the payment room`() = runTest(dispatcher) {
        val first = selection()
        val second = selection("102")
        val source = source(first)
        val settings = FakeElectricityAlertSettings()
        val paymentViewModel = viewModel(source, settings)
        val settingsViewModel = viewModel(source, settings)
        advanceUntilIdle()
        val paymentDetails = paymentViewModel.fullRoomDetails.value
        assertEquals(room(), paymentDetails)

        source.roomResult = AhuResult.Success(
            room().copy(details = room().details.copy(room = "102", roomName = "102"))
        )
        settingsViewModel.onRoomSelected(requireNotNull(second.room))
        advanceUntilIdle()
        settingsViewModel.addCurrentAlertRoom(5)
        advanceUntilIdle()

        assertEquals(first.room, paymentViewModel.selectedRoom.value)
        assertEquals(paymentDetails, paymentViewModel.fullRoomDetails.value)
        assertEquals(second.room, settingsViewModel.selectedRoom.value)
        assertEquals(second, settings.configuration.value.rooms.single().selection)
        assertEquals(5, paymentViewModel.alertConfiguration.value.rooms.single().thresholdDays)
    }

    private fun viewModel(
        source: FakeElectricityDepositSource,
        settings: FakeElectricityAlertSettings = FakeElectricityAlertSettings()
    ) = ElectricityDepositViewModel(
        source, FakeBehaviorRecorder(), EmptyPresetSuggestions, FakePaymentKeyboardSetting(), settings
    )

    private fun source(selection: RoomSelectionInfo) = FakeElectricityDepositSource(
        controller = selection.controller ?: ElectricityController.C,
        selection = selection,
        optionsResult = AhuResult.Success(listOfNotNull(selection.room)),
        roomResult = AhuResult.Success(room()),
        payResult = AhuResult.Success(RechargeReceipt("order-1"))
    )

    private fun selection(roomNumber: String = "101") = RoomSelectionInfo(
        campus = CampusDataItem("磬湖校区", "campus"),
        building = CampusDataItem("宿舍", "building"),
        floor = CampusDataItem("1层", "floor"),
        room = CampusDataItem(roomNumber, roomNumber),
        controller = ElectricityController.C
    )

    private fun room() = ElectricityRoom(
        displayInfo = "余额 20 元",
        details = ElectricityRoomDetails(
            area = "campus",
            buildingName = "宿舍",
            areaName = "磬湖校区",
            floorName = "1层",
            floor = "floor",
            aid = "aid",
            account = "account",
            building = "building",
            room = "room",
            roomName = "101"
        )
    )

    private object EmptyPresetSuggestions : PresetSuggestions {
        override suspend fun rank(domain: SemanticDomain): List<PresetCandidate> = emptyList()
        override suspend fun markExposed(candidate: PresetCandidate): PresetInteractionToken? = null
        override suspend fun apply(candidate: PresetCandidate): AppliedPreset? = null
        override fun expire(token: PresetInteractionToken?) = Unit
        override suspend fun recordNaturalSubmission(
            submission: PresetSubmission,
            interactionToken: PresetInteractionToken?,
            candidatesAtOpportunity: List<PresetCandidate>
        ) = Unit
    }
}
