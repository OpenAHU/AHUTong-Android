package com.ahu.ahutong.assistant.safety

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantWriteJournalTest {

    private fun args(amount: String, password: String = "123456") = JsonObject().apply {
        addProperty("amount", amount)
        addProperty("paymentPassword", password)
    }

    @Test
    fun blocksWhenPreviousResultIsUnknown() = runBlocking {
        val journal = InMemoryAssistantWriteJournal()
        val arguments = args("10")
        journal.begin("recharge_card", arguments)
        assertNotNull(journal.check("recharge_card", arguments))

        journal.finish("recharge_card", arguments, AssistantWriteOutcome.UNKNOWN)
        assertNotNull(journal.check("recharge_card", arguments))
    }

    @Test
    fun allowsAfterKnownResult() = runBlocking {
        val journal = InMemoryAssistantWriteJournal()
        val arguments = args("10")
        journal.begin("recharge_card", arguments)
        journal.finish("recharge_card", arguments, AssistantWriteOutcome.KNOWN_FAILURE)
        assertNull(journal.check("recharge_card", arguments))

        journal.begin("recharge_card", arguments)
        journal.finish("recharge_card", arguments, AssistantWriteOutcome.SUCCESS)
        assertNull(journal.check("recharge_card", arguments))
    }

    @Test
    fun passwordDoesNotChangeFingerprint() = runBlocking {
        val journal = InMemoryAssistantWriteJournal()
        journal.begin("recharge_card", args("10", password = "111111"))
        journal.finish("recharge_card", args("10", password = "111111"), AssistantWriteOutcome.UNKNOWN)

        // 换了支付密码，但金额相同：仍然算同一次未知操作。
        assertNotNull(journal.check("recharge_card", args("10", password = "222222")))
        // 不同金额不算重复。
        assertNull(journal.check("recharge_card", args("20", password = "222222")))
    }
}
