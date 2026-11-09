package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantCapabilityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCapabilitiesTest {

    private val expectedReadIds = setOf(
        "get_schedule",
        "get_assignments",
        "get_work_detail",
        "confirm_homework_help",
        "get_grades",
        "get_gpa_rank",
        "get_exams",
        "get_balance",
        "get_bills",
        "get_electricity",
        "get_electricity_conditions",
        "get_attendance",
        "get_program_completion",
        "get_evaluation_tasks",
        "get_canteen_ranking",
        "get_free_rooms",
        "get_lost_found",
        "get_notices",
        "get_mail",
        "get_phone_book",
        "get_course_tasks",
        "get_weather",
        "web_search",
    )

    private val expectedActionIds = setOf("add_calendar_event")

    private val expectedWriteIds = setOf(
        "recharge_card",
        "recharge_electricity",
        "recharge_bathroom",
        "recharge_network",
        "submit_evaluation",
        "send_mail",
        "publish_lost_found",
        "delete_lost_found",
        "switch_electricity_condition",
    )

    @Test
    fun registersEveryCapabilityOnce() {
        val capabilities = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
        val ids = capabilities.map { it.spec.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(expectedReadIds + expectedActionIds + expectedWriteIds, ids.toSet())
    }

    @Test
    fun actionCapabilitiesOnlyNavigate() {
        val capabilities = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
        capabilities
            .filter { it.spec.id in expectedActionIds }
            .forEach { capability ->
                assertEquals(AssistantCapabilityKind.NAVIGATE, capability.spec.kind)
                assertTrue(capability.spec.openRoute != null)
            }
    }

    @Test
    fun academicConfirmationIsReadOnlyButRequiresApproval() {
        val capability = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
            .first { it.spec.id == "confirm_homework_help" }
        assertEquals(AssistantCapabilityKind.READ, capability.spec.kind)
        assertTrue(capability.spec.requiresConfirmation)
        assertTrue(capability.spec.silent)
        assertEquals("继续解答", capability.spec.confirmationAction)
    }

    @Test
    fun writeCapabilitiesRequireConfirmation() {
        val capabilities = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
        capabilities
            .filter { it.spec.id in expectedWriteIds }
            .forEach { capability ->
                assertEquals(AssistantCapabilityKind.WRITE, capability.spec.kind)
                assertTrue(capability.spec.requiresConfirmation)
                assertTrue(capability.spec.openRoute != null)
            }
    }

    @Test
    fun paymentCapabilitiesAskForThePasswordLocally() {
        val capabilities = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
        val paymentIds = setOf("recharge_electricity", "recharge_bathroom", "recharge_network")
        capabilities
            .filter { it.spec.id in paymentIds }
            .forEach { capability -> assertTrue(capability.spec.requiresPaymentPassword) }
    }

    @Test
    fun readCapabilitiesDeclareSchemas() {
        val capabilities = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
        capabilities
            .filter { it.spec.id in expectedReadIds }
            .forEach { capability ->
                assertEquals("object", capability.spec.parameters.get("type").asString)
                assertTrue(capability.spec.description.isNotBlank())
            }
    }
}
