package com.ahu.ahutong.appwidget

internal suspend fun runWidgetBroadcast(
    finish: () -> Unit,
    scheduleNext: () -> Unit = {},
    enqueue: suspend () -> Unit
) {
    try {
        enqueue()
    } finally {
        try {
            scheduleNext()
        } finally {
            finish()
        }
    }
}
