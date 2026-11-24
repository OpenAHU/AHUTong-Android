package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.DormitoryInfo
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityOptionLevel
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
import com.ahu.ahutong.testing.FakeElectricityAlertSettings
import com.ahu.ahutong.testing.FakeElectricityDepositSource
import com.ahu.ahutong.testing.FakePaymentKeyboardSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** 进电费页时按本机宿舍信息预选楼栋的行为。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElectricityDepositAutoSelectTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `无历史记录时按宿舍楼栋预选楼栋并加载楼层`() = runTest(dispatcher) {
        val source = source(
            controller = ElectricityController.A,
            dormitory = dormitory()
        )
        source.optionsHandler = { query ->
            when (query.level) {
                ElectricityOptionLevel.Initial -> AhuResult.Success(buildings())
                ElectricityOptionLevel.Floors -> AhuResult.Success(floors())
                else -> AhuResult.Success(listOf(CampusDataItem("2401", "r2401")))
            }
        }

        val subject = viewModel(source)
        advanceUntilIdle()

        assertEquals("榴园二号楼", subject.selectedBuilding.value?.name)
        assertEquals(listOf("一层", "四层"), subject.floorsList.value.map { it.name })
        assertNull(subject.selectedFloor.value)
        assertNull(subject.selectedRoom.value)
    }

    @Test
    fun `按宿舍号逐层查找自动选中楼层与房间`() = runTest(dispatcher) {
        val source = source(controller = ElectricityController.A, dormitory = dormitory())
        source.optionsHandler = { query ->
            when (query.level) {
                ElectricityOptionLevel.Initial -> AhuResult.Success(buildings())
                ElectricityOptionLevel.Buildings -> AhuResult.Success(buildings())
                ElectricityOptionLevel.Floors -> AhuResult.Success(floors())
                ElectricityOptionLevel.Rooms -> {
                    val rooms = if (query.selection.floor?.name == "四层") {
                        listOf(CampusDataItem("2401", "r2401"), CampusDataItem("2439", "r2439"))
                    } else {
                        listOf(CampusDataItem("1401", "r1401"))
                    }
                    AhuResult.Success(rooms)
                }
            }
        }

        val subject = viewModel(source)
        advanceUntilIdle()

        assertEquals("四层", subject.selectedFloor.value?.name)
        assertEquals("2439", subject.selectedRoom.value?.name)
        assertEquals(listOf("2401", "2439"), subject.roomsList.value.map { it.name })
        assertEquals(1, source.roomQueries.size)
    }

    @Test
    fun `同一楼栋多种写法时不预选`() = runTest(dispatcher) {
        val source = source(
            controller = ElectricityController.A,
            dormitory = dormitory()
        )
        source.optionsResult = AhuResult.Success(
            listOf(CampusDataItem("榴园2号楼", "b1"), CampusDataItem("榴园二号楼", "b2"))
        )

        val subject = viewModel(source)
        advanceUntilIdle()

        assertNull(subject.selectedBuilding.value)
    }

    @Test
    fun `没有住宿信息时不预选`() = runTest(dispatcher) {
        val source = source(controller = ElectricityController.A, dormitory = null)
        source.optionsResult = AhuResult.Success(buildings())

        val subject = viewModel(source)
        advanceUntilIdle()

        assertNull(subject.selectedBuilding.value)
        assertEquals(3, subject.buildingsList.value.size)
    }

    @Test
    fun `楼栋列表没有对应项时不预选`() = runTest(dispatcher) {
        val source = source(controller = ElectricityController.A, dormitory = dormitory())
        source.optionsResult = AhuResult.Success(listOf(CampusDataItem("梅园一号楼", "m1")))

        val subject = viewModel(source)
        advanceUntilIdle()

        assertNull(subject.selectedBuilding.value)
    }

    @Test
    fun `电控C先按宿舍校区选校区再预选楼栋`() = runTest(dispatcher) {
        val source = source(controller = ElectricityController.C, dormitory = dormitory())
        source.optionsHandler = { query ->
            when (query.level) {
                ElectricityOptionLevel.Initial -> AhuResult.Success(
                    listOf(CampusDataItem("龙河校区", "c1"), CampusDataItem("磬苑校区", "c2"))
                )
                ElectricityOptionLevel.Buildings -> AhuResult.Success(buildings())
                else -> AhuResult.Success(listOf(CampusDataItem("2层", "f2")))
            }
        }

        val subject = viewModel(source)
        advanceUntilIdle()

        assertEquals("磬苑校区", subject.selectedCampus.value?.name)
        assertEquals("榴园二号楼", subject.selectedBuilding.value?.name)
    }

    @Test
    fun `已有历史选择时不按宿舍覆盖`() = runTest(dispatcher) {
        val history = RoomSelectionInfo(
            campus = null,
            building = CampusDataItem("梅园一号楼", "m1"),
            floor = CampusDataItem("3层", "f3"),
            room = CampusDataItem("301", "r301"),
            controller = ElectricityController.A
        )
        val source = source(controller = ElectricityController.A, dormitory = dormitory(), selection = history)
        source.optionsResult = AhuResult.Success(buildings())

        val subject = viewModel(source)
        advanceUntilIdle()

        assertEquals("梅园一号楼", subject.selectedBuilding.value?.name)
    }

    private fun dormitory() = DormitoryInfo(
        campus = "磬苑校区",
        building = "榴园2号楼",
        room = "2439",
        resident = true,
        cachedAt = 1L
    )

    private fun floors() = listOf(
        CampusDataItem("一层", "f1"),
        CampusDataItem("四层", "f4")
    )

    private fun buildings() = listOf(
        CampusDataItem("榴园一号楼", "b1"),
        CampusDataItem("榴园二号楼", "b2"),
        CampusDataItem("榴园三号楼", "b3")
    )

    private fun viewModel(source: FakeElectricityDepositSource) = ElectricityDepositViewModel(
        source,
        FakeBehaviorRecorder(),
        EmptyPresetSuggestions,
        FakePaymentKeyboardSetting(),
        FakeElectricityAlertSettings()
    )

    private fun source(
        controller: ElectricityController,
        dormitory: DormitoryInfo?,
        selection: RoomSelectionInfo? = null
    ) = FakeElectricityDepositSource(
        controller = controller,
        selection = selection,
        optionsResult = AhuResult.Success(buildings()),
        roomResult = AhuResult.Success(
            ElectricityRoom(
                displayInfo = "余额 20 元",
                details = ElectricityRoomDetails(
                    area = null,
                    buildingName = "榴园二号楼",
                    areaName = null,
                    floorName = "2层",
                    floor = "f2",
                    aid = null,
                    account = "account",
                    building = "b2",
                    room = "r",
                    roomName = "2439"
                )
            )
        ),
        payResult = AhuResult.Success(RechargeReceipt("order-1")),
        dormitory = dormitory
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
