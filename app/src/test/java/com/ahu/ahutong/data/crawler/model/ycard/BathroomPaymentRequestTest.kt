package com.ahu.ahutong.data.crawler.model.ycard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BathroomPaymentRequestTest {
    @Test
    fun `order request matches the signed payment contract`() {
        val params = BathroomRequest(
            bathroom = "竹园/龙河",
            amount = "0.1",
            thirdPartyJson = "{\"projectId\":945}",
            timestamp = TIMESTAMP,
            nonce = NONCE
        ).toMap()

        assertEquals("409", params["feeitemid"])
        assertEquals("https://ycard.ahu.edu.cn/plat", params["redirect_url"])
        assertEquals("", params["abstracts"])
        assertEquals(ORDER_SIGNATURE, params["SIGN"])
    }

    @Test
    fun `payment preparation requests a fresh password map`() {
        val params = BathroomPayPrepareRequest(
            orderId = ORDER_ID,
            timestamp = TIMESTAMP,
            nonce = NONCE
        ).toMap()

        assertEquals("2", params["paystep"])
        assertEquals("ACCOUNTTSM", params["paytype"])
        assertEquals("64", params["paytypeid"])
        assertEquals(PREPARE_SIGNATURE, params["SIGN"])
    }

    @Test
    fun `final payment uses the dynamic uuid and password map`() {
        val params = BathroomPayRequest(
            orderId = ORDER_ID,
            plaintext = "012345",
            uuid = "dynamic-uuid",
            passwordMap = "7685349012",
            timestamp = TIMESTAMP,
            nonce = NONCE
        ).toMap()

        assertEquals("dynamic-uuid", params["uuid"])
        assertEquals("789453", params["password"])
        assertEquals("wechat-mp", params["userAgent"])
        assertEquals("1", params["isWX"])
        assertEquals(PAY_SIGNATURE, params["SIGN"])
    }

    @Test
    fun `invalid password maps are rejected before payment`() {
        assertFailsWith<IllegalArgumentException> {
            BathroomPayRequest(
                orderId = ORDER_ID,
                plaintext = "012345",
                uuid = "dynamic-uuid",
                passwordMap = "0123456788",
                timestamp = TIMESTAMP,
                nonce = NONCE
            )
        }
    }

    private companion object {
        const val ORDER_ID = "test-order-id"
        const val TIMESTAMP = "20260909161303"
        const val NONCE = "abc123def45"
        const val ORDER_SIGNATURE =
            "50A5158D409E45181F27DA0DFD71436F23E08A8B2708432D5AE5A5C33113F454"
        const val PREPARE_SIGNATURE =
            "81AFB2483DFF8416FE51C68FA60A6E7AECE1322965688B1F50E4D76E51B627DE"
        const val PAY_SIGNATURE =
            "2C65FC45C5C542762F2C710C5D04A9DEA97B70FA0FD39A7CAFE63FA90372AED8"
    }
}
