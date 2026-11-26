package com.ahu.ahutong.cli

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CliProtocolTest {
    @Test fun approvalIsBoundToArgumentsAndAccountAndIsSingleUse() {
        var now = 100L
        val approvals = CliApprovals { now }
        val ticket = approvals.prepare("pay:{amount:10}", 4)
        assertFalse(approvals.consume(ticket, "pay:{amount:100}", 4))
        assertFalse(approvals.consume(ticket, "pay:{amount:10}", 4))
        val changedAccount = approvals.prepare("pay:{amount:10}", 4)
        assertFalse(approvals.consume(changedAccount, "pay:{amount:10}", 5))
        val expires = approvals.prepare("pay:{amount:10}", 4)
        now += 120_000
        assertFalse(approvals.consume(expires, "pay:{amount:10}", 4))
        val valid = approvals.prepare("pay:{amount:10}", 4)
        assertTrue(approvals.consume(valid, "pay:{amount:10}", 4))
        assertFalse(approvals.consume(valid, "pay:{amount:10}", 4))
    }

    @Test fun applicationUidCannotReachShellBridge() {
        assertTrue(CliAccess.permitted(2000))
        assertTrue(CliAccess.permitted(0))
        assertFalse(CliAccess.permitted(1000))
        assertFalse(CliAccess.permitted(10200))
        assertFalse(CliAccess.permitted(202000))
    }

    @Test fun rejectsUnknownFieldsMissingValuesAndWrongTypes() {
        val schema = CliSchema.of("amount:number bank:string optional?:boolean")
        CliSchema.validate(schema, JsonParser.parseString("""{"amount":10,"bank":"cmb"}"""))
        listOf("""{"amount":"10","bank":"cmb"}""", """{"amount":10}""",
            """{"amount":10,"bank":"cmb","password":"secret"}""").forEach { input ->
            try {
                CliSchema.validate(schema, JsonParser.parseString(input))
                fail("Accepted invalid input")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
