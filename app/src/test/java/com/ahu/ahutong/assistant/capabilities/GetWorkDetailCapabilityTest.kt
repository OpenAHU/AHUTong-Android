package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantOutcome
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GetWorkDetailCapabilityTest {
    @Test
    fun officialTrialModelRejectsQuestionFetchBeforeReadingAssignments() = runBlocking {
        var assignmentsRead = false
        val capability = GetWorkDetailCapability(
            works = {
                assignmentsRead = true
                emptyList()
            },
            officialTrialActive = { true },
        )
        val outcome = capability.execute(JsonObject().apply { addProperty("work_id", "42") })
        val failure = outcome as AssistantOutcome.Failure
        assertEquals("trial_model_unsupported", failure.code)
        assertEquals("试用模型不提供解题服务，若要使用请自行配置模型", failure.message)
        assertFalse(assignmentsRead)
    }
}
