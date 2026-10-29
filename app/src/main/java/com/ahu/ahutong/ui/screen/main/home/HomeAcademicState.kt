package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleSnapshot
import com.ahu.ahutong.data.model.ScheduleConfigBean

internal class HomeAcademicValue<T>(initialValue: T) {
    val state: MutableState<T> = mutableStateOf(initialValue)
    var loadedKeys: List<Any?>? = null
}

/** Owned by Main so navigation cannot turn an already loaded timetable into an empty one. */
internal class HomeAcademicState {
    val undergraduate = HomeAcademicValue<ScheduleConfigBean?>(null)
    val graduate = HomeAcademicValue<Pair<String?, GmisScheduleSnapshot?>?>(null)
}

@Composable
internal fun <T> rememberHomeAcademicValue(
    retained: HomeAcademicValue<T>,
    vararg keys: Any?,
    load: suspend () -> T
): State<T> {
    val loadKeys = keys.toList()
    LaunchedEffect(retained, loadKeys) {
        if (retained.loadedKeys != loadKeys || retained.state.value == null) {
            val loaded = load()
            retained.state.value = loaded
            retained.loadedKeys = loadKeys
        }
    }
    return retained.state
}
