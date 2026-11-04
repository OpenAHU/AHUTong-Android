package com.ahu.ahutong.assistant.safety

import com.ahu.ahutong.assistant.AssistantCapabilityKind
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantToolPolicyTest {

    private fun spec(
        kind: AssistantCapabilityKind,
        sensitivity: AssistantSensitivity = AssistantSensitivity.PUBLIC,
    ) = AssistantCapabilitySpec(
        id = "x",
        title = "x",
        description = "x",
        parameters = JsonParser.parseString("""{"type":"object"}""").asJsonObject,
        sensitivity = sensitivity,
        kind = kind,
    )

    @Test
    fun readCapabilitiesRunWithoutApproval() {
        assertEquals(
            AssistantPolicyDecision.ALLOW,
            AssistantToolPolicy.decide(spec(AssistantCapabilityKind.READ)),
        )
        assertEquals(
            AssistantPolicyDecision.ALLOW,
            AssistantToolPolicy.decide(spec(AssistantCapabilityKind.NAVIGATE)),
        )
    }

    @Test
    fun writesAlwaysRequireApproval() {
        assertEquals(
            AssistantPolicyDecision.REQUIRE_APPROVAL,
            AssistantToolPolicy.decide(spec(AssistantCapabilityKind.WRITE)),
        )
        assertEquals(
            AssistantPolicyDecision.REQUIRE_APPROVAL,
            AssistantToolPolicy.decide(
                spec(AssistantCapabilityKind.WRITE, AssistantSensitivity.FINANCIAL)
            ),
        )
    }
}
