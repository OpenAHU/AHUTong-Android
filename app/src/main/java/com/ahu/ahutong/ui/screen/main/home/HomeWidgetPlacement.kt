package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.HomeWidgetLayoutFamily
import com.ahu.ahutong.data.dao.HomeWidgetDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shares external slot changes with the already composed Home screen. */
object HomeWidgetPlacement {
    const val NOTICE_WIDGET_ID = HomeWidgetDefaults.NOTICE_WIDGET_ID
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision

    fun currentSlots(): List<String?> {
        val family = HomeWidgetLayoutFamily.RADIANT
        val saved = if (AHUCache.hasStoredHomeWidgetSlots(family)) {
            AHUCache.getHomeWidgetSlots(family)
        } else {
            AHUCache.getHomeWidgetSlots(HomeWidgetLayoutFamily.CLASSIC)
        }
        val known = HomeWidgetRegistry.availableWidgets().mapTo(mutableSetOf()) { it.id }
        val seen = mutableSetOf<String>()
        return List(HomeWidgetRegistry.slotCountRadiant) { index ->
            saved.getOrNull(index)?.takeIf { it in known && seen.add(it) }
        }
    }

    @Synchronized
    fun placeNoticeAt(index: Int): Boolean {
        val slots = currentSlots()
        if (index !in slots.indices) return false
        if (NOTICE_WIDGET_ID in slots) return true
        val updated = slots.toMutableList().apply { this[index] = NOTICE_WIDGET_ID }
        AHUCache.saveHomeWidgetSlots(HomeWidgetLayoutFamily.RADIANT, updated)
        mutableRevision.value += 1
        return true
    }

    @Synchronized
    fun removeNotice(): Boolean {
        val slots = currentSlots()
        if (NOTICE_WIDGET_ID !in slots) return true
        AHUCache.saveHomeWidgetSlots(
            HomeWidgetLayoutFamily.RADIANT,
            slots.map { if (it == NOTICE_WIDGET_ID) null else it }
        )
        mutableRevision.value += 1
        return true
    }
}
