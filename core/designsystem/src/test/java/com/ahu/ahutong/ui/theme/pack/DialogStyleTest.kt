package com.ahu.ahutong.ui.theme.pack

import kotlin.test.Test
import kotlin.test.assertEquals

class DialogStyleTest {
    @Test
    fun `dialog slot overrides and persists independently`() {
        val overrides = mapOf(
            ComponentSlotId.Button to SlotSource.RADIANT,
            ComponentSlotId.Dialog to SlotSource.MIUIX
        )
        val restored = parseSlotOverrides(overrides.serializeSlotOverrides())
        assertEquals(overrides, restored)
        assertEquals(DialogStyle.Miuix, MaterialComponentPack.mixing(restored).dialogStyle)
        val legacy = parseSlotOverrides("Button=material;BottomSheet=miuix")
        assertEquals(DialogStyle.Radiant, RadiantComponentPack.mixing(legacy).dialogStyle)
    }
}
