package com.ahu.ahutong

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.data.model.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineStateSeederTest {
    @Test
    fun seedDeterministicLoggedInMockState() {
        seed("standard_student")
        val paymentCode = runBlocking { AHURepository.getQrcode().getOrThrow() }
        assertEquals("AHUTONG-MOCK-PAYMENT-CODE", paymentCode)
    }

    @Test
    fun seedEmptyMockState() {
        seed("empty_campus")
    }

    @Test
    fun seedNetworkErrorMockState() {
        seed("network_error")
    }

    @Test
    fun seedSlowLoadingMockState() {
        seed("standard_student")
        MockScenarioController.saveEndpointText(
            "behavior",
            """{"networkMode":"Success","latencyMs":10000,"errorMessage":"Mock 场景模拟接口失败","emptyDataMessage":"Mock 场景模拟暂无数据","paymentMode":"Success","loginState":"Valid"}"""
        )
    }

    private fun seed(scenario: String) {
        AHUCache.clearAll()
        AHUCache.saveCurrentUser(User("测试同学", "AB220001"))
        AHUCache.setAgreementAccepted()
        AHUCache.setPrivacyAccepted()
        AHUCache.setBusinessAccepted()
        AHUCache.setMockData(true)
        AHUCache.saveMockCurrentTimeMillis(1_784_023_200_000)
        MockScenarioController.resetAllEndpointText()
        MockScenarioController.selectScenario(scenario)
    }
}
