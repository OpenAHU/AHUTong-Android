package com.ahu.ahutong.ui.screen

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ElectricityAlertVisibilityTest {
    @Test fun `payment QR and background suppress reminder while other authenticated screens allow it`() {
        assertFalse(canShowElectricityAlert(true, true, true))
        assertFalse(canShowElectricityAlert(false, true, false))
        assertFalse(canShowElectricityAlert(true, false, false))
        assertTrue(canShowElectricityAlert(true, true, false))
    }
}
