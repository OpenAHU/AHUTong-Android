package com.ahu.ahutong.ui.screen

import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PaymentQrVisibilityTest {
    @Test fun `profile activation cannot erase visibility of the actual payment QR`() {
        val commands = PaymentQrOpenCommandStore()
        commands.setVisible(true)
        commands.activate(1, 1)
        commands.activate(1, 2)
        assertTrue(commands.visible.value)
        commands.setVisible(false)
        assertFalse(commands.visible.value)
    }
}
