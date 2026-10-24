package com.ahu.ahutong.data.crawler.model.ycard

import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Home balance and the recharge page must read the same account field. */
class CardInfoBalanceTest {

    @Test
    fun `primary account balance is read in cents`() {
        val info = Gson().fromJson(
            """
            {"code":0,"msg":"","success":true,"data":{"account":null,"sno":null,
             "errmsg":null,"retcode":"0",
             "card":[{"accinfo":[{"balance":90,"name":"电子账户","type":"1"}]}]}}
            """.trimIndent(),
            CardInfo::class.java
        )

        assertEquals(90, info.primaryAccountBalanceCents())
    }

    @Test
    fun `missing card or account info has no balance`() {
        val empty = Gson().fromJson(
            """{"code":0,"msg":"","success":true,"data":{"card":[]}}""",
            CardInfo::class.java
        )
        assertNull(empty.primaryAccountBalanceCents())
    }
}
