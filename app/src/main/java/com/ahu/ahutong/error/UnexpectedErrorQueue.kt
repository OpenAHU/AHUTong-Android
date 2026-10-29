package com.ahu.ahutong.error

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local only: no Activity, Throwable, credentials or persistent crash files are retained. */
internal class UnexpectedErrorQueue(
    private val capacity: Int = 3,
    private val duplicateWindowMillis: Long = 60_000,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val _reports = MutableStateFlow<List<UnexpectedErrorReport>>(emptyList())
    val reports = _reports.asStateFlow()
    private val recent = linkedMapOf<String, Long>()
    private var nextId = 0L

    @Synchronized
    fun enqueue(details: String, sentryEventId: String? = null): Boolean {
        val now = nowMillis()
        recent.entries.removeAll { now < it.value || now - it.value >= duplicateWindowMillis }
        if (_reports.value.any { it.details == details } || recent.containsKey(details)) return false
        if (_reports.value.size >= capacity) return false
        recent[details] = now
        while (recent.size > 16) recent.remove(recent.keys.first())
        _reports.value = _reports.value + UnexpectedErrorReport(++nextId, details, sentryEventId)
        return true
    }

    @Synchronized
    fun dismiss(id: Long) {
        _reports.value = _reports.value.filterNot { it.id == id }
    }
}

internal object UnexpectedErrors {
    val queue = UnexpectedErrorQueue()
}
