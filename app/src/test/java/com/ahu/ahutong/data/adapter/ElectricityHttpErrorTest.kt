package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.toUserMessage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class ElectricityHttpErrorTest {
    @Test fun `missing campus card token has an actionable message instead of Retrofit response text`() {
        val error = electricityHttpError(401)
        assertEquals(AhuError.Unauthorized("校园卡登录暂不可用，请稍后重试"), error)
        assertFalse(error.toUserMessage().contains("Response.error()"))
    }

    @Test fun `upstream failure retains the HTTP status`() {
        assertEquals(AhuError.Server(503, "电控服务暂不可用，请稍后重试（503）"), electricityHttpError(503))
    }
}
