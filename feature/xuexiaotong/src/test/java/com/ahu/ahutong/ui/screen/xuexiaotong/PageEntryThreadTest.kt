package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.lifecycle.ViewModelStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.testing.FakeChaoxingReminders
import com.ahu.ahutong.testing.FakeChaoxingSession
import com.ahu.ahutong.testing.FakeChaoxingStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PageEntryThreadTest {
    @Test
    fun `cache restore leaves main responsive and cannot restore a logged out session`() {
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "learning-page-main") }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val readFinished = CountDownLatch(1)
        val frameCallback = CountDownLatch(1)
        val cacheThread = AtomicReference<String>()
        val subject = AtomicReference<XuexiaotongViewModel>()
        val viewModels = ViewModelStore()
        val storage = object : ChaoxingStore by FakeChaoxingStore() {
            override fun works(): List<Work> {
                cacheThread.set(Thread.currentThread().name)
                reading.countDown()
                try {
                    check(release.await(5, TimeUnit.SECONDS))
                    return listOf(Work(title = "cached work"))
                } finally {
                    readFinished.countDown()
                }
            }
        }
        Dispatchers.setMain(dispatcher)
        try {
            scope.launch {
                val vm = XuexiaotongViewModel(FakeChaoxingSession(), storage, FakeChaoxingReminders(), Dispatchers.IO)
                viewModels.put("learning", vm)
                subject.set(vm)
            }
            assertTrue(reading.await(3, TimeUnit.SECONDS), "Page never entered the cache path")
            scope.launch { frameCallback.countDown() }
            assertTrue(frameCallback.await(1, TimeUnit.SECONDS), "Cache restore blocks the UI event loop")
            assertNotEquals("learning-page-main", cacheThread.get())
            runBlocking(dispatcher) { subject.get().logout() }
            release.countDown()
            assertTrue(readFinished.await(3, TimeUnit.SECONDS))
            runBlocking(dispatcher) {
                assertFalse(subject.get().loggedIn.value)
                assertTrue(subject.get().works.value.isEmpty())
            }
        } finally {
            release.countDown()
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            runBlocking(dispatcher) { viewModels.clear() }
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }
}
