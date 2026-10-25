package com.ahu.ahutong.appwidget

import kotlinx.coroutines.CancellationException

internal suspend fun renderWidgetStyles(
    glance: suspend () -> Unit,
    adaptive: suspend () -> Unit,
    onFailure: (String, Exception) -> Unit = { _, _ -> }
): Boolean {
    var rendered = true
    for ((style, render) in listOf("Glance" to glance, "adaptive" to adaptive)) {
        try {
            render()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A broken widget style must not prevent the other style from updating.
            rendered = false
            onFailure(style, error)
        }
    }
    return rendered
}
