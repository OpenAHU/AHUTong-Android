package com.ahu.ahutong.ext

import com.ahu.ahutong.core.common.UserNotice
import com.ahu.ahutong.core.common.UserNoticeHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoroutineScopeTest {
    @Test fun rootFailureUsesUnhandledDiagnosticEntryAndPreservesOtherJobs() = runBlocking {
        val failures = mutableListOf<Throwable>()
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) = kotlin.error("wrong entry")
            override fun reportUnhandled(error: Throwable) { failures += error }
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val original = IllegalStateException("root coroutine failure")
            scope.launchSafe { throw original }.join()
            var siblingRan = false
            scope.launchSafe { siblingRan = true }.join()
            assertEquals(1, failures.size)
            assertSame(original, failures.single())
            assertTrue(siblingRan)
        } finally {
            scope.cancel()
            UserNoticeHolder.install(object : UserNotice { override fun showFailure(error: Throwable) = Unit })
        }
    }

    @Test fun normalCancellationDoesNotReachUnhandledReporter() = runBlocking {
        var calls = 0
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) { calls++ }
            override fun reportUnhandled(error: Throwable) { calls++ }
        })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            scope.launchSafe { throw CancellationException("cancelled") }.join()
            assertEquals(0, calls)
        } finally {
            scope.cancel()
            UserNoticeHolder.install(object : UserNotice { override fun showFailure(error: Throwable) = Unit })
        }
    }
}
