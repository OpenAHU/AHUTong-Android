package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantCapabilityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCapabilitiesTest {

    private val expectedReadIds = setOf(
        "get_schedule",
        "get_assignments",
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
    )

    private val expectedActionIds = setOf(
        "recharge_card",
        "recharge_electricity",
        "recharge_bathroom",
        "recharge_network",
        "submit_evaluation",
        "send_mail",
        "publish_lost_found",
        "delete_lost_found",
        "switch_electricity_condition",
        "add_calendar_event",
    )

    @Test
    fun registersEveryCapabilityOnce() {
        val capabilities = AssistantCapabilities.all(config = { null }, cachedSchedule = { null })
        val ids = capabilities.map { it.spec.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(expectedReadIds + expectedActionIds, ids.toSet())
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
