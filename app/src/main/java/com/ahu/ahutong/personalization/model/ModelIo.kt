package com.ahu.ahutong.personalization.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Model entry points are suspend functions, but their AtomicFile work is blocking. */
internal suspend fun <T> Mutex.withModelIoLock(block: () -> T): T =
    withContext(Dispatchers.IO) { withLock { block() } }
