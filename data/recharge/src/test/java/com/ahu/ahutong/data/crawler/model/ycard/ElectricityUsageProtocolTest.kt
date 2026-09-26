package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ElectricityUsageProtocolTest {
    @Test
    fun `reads A and B remaining energy without mixing currency`() {
        assertEquals(0.50, balance("房间当前剩余电量:0.50 度"))
        assertEquals(5.86, balance("房间当前剩余电量:5.86 度"))
    }

    @Test
    fun `C balance is energy and its price is never mistaken for balance`() {
        assertEquals(6.79, balance("房间当前剩余电量6.79，电量单价0.56"))
    }

    @Test
    fun `missing balance and unexpected monetary unit fail instead of becoming zero`() {
        for (body in listOf(
            """{"code":200,"map":{"showData":{}}}""",
            """{"code":200,"map":{"showData":{"信息":"余额6.79元"}}}""",
            """{"code":200,"map":{"showData":{"信息":"房间当前剩余电量6.79元"}}}""",
            "<html>登录</html>"
        )) {
            assertIs<AhuError.ProtocolChanged>(
                assertIs<AhuResult.Failure>(ElectricityUsageProtocol.balance(body)).error
            )
        }
    }

    @Test
    fun `record type metadata is not a valid empty history`() {
        val result = ElectricityUsageProtocol.history(
            """{"code":200,"recordType":[{"rtype":"dayEnergy","ispage":"true"}]}"""
        )
        assertIs<AhuError.ProtocolChanged>(assertIs<AhuResult.Failure>(result).error)
        assertEquals(
            emptyList(),
            assertIs<AhuResult.Success<ElectricityUsageHistoryPage>>(
                ElectricityUsageProtocol.history("""{"code":200,"data":[]}""")
            ).value.records
        )
    }

    @Test
    fun `retains separate same day readings for daily aggregation and optional pagination total`() {
        val result = ElectricityUsageProtocol.history(
            """{"code":200,"total":27,"data":[
                {"日期":"2026-09-12","度数":"2.07度"},
                {"日期":"2026-09-12","度数":"3.45度"}
            ]}"""
        )
        val page = assertIs<AhuResult.Success<ElectricityUsageHistoryPage>>(result).value

        assertEquals(27, page.total)
        assertEquals(2, page.records.size)
        assertEquals(LocalDate.of(2026, 9, 12), page.records[0].date)
        assertEquals(5.52, page.records.sumOf { it.kwh }, 1e-10)
        assertNull(
            assertIs<AhuResult.Success<ElectricityUsageHistoryPage>>(
                ElectricityUsageProtocol.history("""{"code":200,"data":[]}""")
            ).value.total
        )
    }

    @Test
    fun `malformed date quantity unit and pagination fail as a whole`() {
        for (row in listOf(
            """{"日期":"2026-09-31","度数":"1度"}""",
            """{"日期":"2026-09-12","度数":"-1度"}""",
            """{"日期":"2026-09-12","度数":"1元"}""",
            """{"日期":"2026-09-12"}"""
        )) {
            assertIs<AhuError.ProtocolChanged>(
                assertIs<AhuResult.Failure>(
                    ElectricityUsageProtocol.history("""{"code":200,"data":[$row]}""")
                ).error
            )
        }
        assertIs<AhuResult.Failure>(
            ElectricityUsageProtocol.history("""{"code":200,"total":-1,"data":[]}""")
        )
    }

    @Test
    fun `business failure cannot supply a successful balance or history`() {
        assertIs<AhuError.Server>(
            assertIs<AhuResult.Failure>(
                ElectricityUsageProtocol.balance(
                    """{"code":500,"map":{"showData":{"信息":"房间当前剩余电量0.00 度"}}}"""
                )
            ).error
        )
        assertIs<AhuError.Server>(
            assertIs<AhuResult.Failure>(
                ElectricityUsageProtocol.history("""{"code":500,"data":[]}""")
            ).error
        )
    }

    private fun balance(info: String): Double =
        assertIs<AhuResult.Success<Double>>(
            ElectricityUsageProtocol.balance("""{"code":200,"map":{"showData":{"信息":"$info"}}}""")
        ).value
}
