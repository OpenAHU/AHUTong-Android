package com.ahu.ahutong.appwidget

import com.ahu.ahutong.background.widgetScheduleReadModel
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.schedule.WidgetScheduleReadModel
import com.ahu.ahutong.data.schedule.WidgetScheduleSnapshot
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class WidgetRenderData(
    val schedule: WidgetScheduleSnapshot,
    val now: Date
) {
    val currentMinutes: Int = Calendar.getInstance().apply { time = now }.let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
}

/** Resolve the dependency and capture storage-backed time before entering widget composition. */
internal suspend fun loadWidgetRenderData(
    readModel: () -> WidgetScheduleReadModel = ::widgetScheduleReadModel,
    now: () -> Date = DebugClock::nowDate
): WidgetRenderData = withContext(Dispatchers.IO) {
    WidgetRenderData(readModel().cachedSnapshot(), now())
}
