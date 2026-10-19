package com.ahu.ahutong.ui.state

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.ahu.ahutong.data.grade.ExamSource
import com.ahu.ahutong.data.grade.GradeSource
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.testing.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class PageEntryThreadTest {
    @get:Rule val instantTaskExecutor = InstantTaskExecutorRule()

    @Test
    fun `opening exams leaves main responsive while reading a slow cache`() = cacheDoesNotBlockMain { read ->
        val source = object : ExamSource by FakeExamSource() {
            override fun cachedExams(): List<Exam> { read(); return emptyList() }
        }
        ExamViewModel(source, FakeGradeSession(), FakeGradeBehavior()).also { it.loadExam() }
    }

    @Test
    fun `constructing grades leaves main responsive while restoring a slow cache`() = cacheDoesNotBlockMain { read ->
        val source = object : GradeSource by FakeGradeSource() {
            override fun cachedProfiles(): List<GradeStudentProfile> { read(); return emptyList() }
        }
        GradeViewModel(FakeGradeBehavior(), FakePresetSuggestions(), source, FakeGradeSession(), FakeGradeWeekConfig())
    }

    private fun cacheDoesNotBlockMain(enter: (() -> Unit) -> ViewModel) {
        val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "page-entry-main") }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val frameCallback = CountDownLatch(1)
        val cacheThread = AtomicReference<String>()
        val store = ViewModelStore()
        Dispatchers.setMain(dispatcher)
        try {
            scope.launch {
                val subject = enter {
                    cacheThread.set(Thread.currentThread().name)
                    reading.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                store.put("page", subject)
            }
            assertTrue(reading.await(3, TimeUnit.SECONDS), "Page never entered the cache path")
            scope.launch { frameCallback.countDown() }
            assertTrue(frameCallback.await(1, TimeUnit.SECONDS), "Cache restore blocks the UI event loop")
            assertNotEquals("page-entry-main", cacheThread.get())
        } finally {
            release.countDown()
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            runBlocking(dispatcher) { store.clear() }
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }
}
