package com.ahu.ahutong.personalization.model

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.personalization.journey.JourneyModelStateStore
import com.ahu.ahutong.personalization.preset.PresetModelStateStore
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class ModelStoreThreadTest {
    @Test
    fun modelFilesAreNeverReadOrWrittenByTheMainThread() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val profile = UUID.randomUUID().toString().replace("-", "")
        val violations = ConcurrentLinkedQueue<String>()
        val original = withContext(Dispatchers.Main) {
            StrictMode.getThreadPolicy().also {
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites()
                    .penaltyListener(Executor { it.run() }) { violation -> violations.add(violation.javaClass.simpleName) }
                    .build())
            }
        }
        try {
            withContext(Dispatchers.Main) {
                val actions = ModelStateStore(context)
                val journeys = JourneyModelStateStore(context)
                val presets = PresetModelStateStore(context)
                try {
                    actions.loadOrCreate(profile)
                    actions.state(profile)
                    journeys.loadOrCreate(profile)
                    journeys.state(profile)
                    presets.state(profile)
                    presets.state(profile)
                } finally {
                    actions.reset(profile)
                    journeys.reset(profile)
                    presets.reset(profile)
                }
            }
            instrumentation.waitForIdleSync()
            assertTrue("Model disk operations reached main: $violations", violations.isEmpty())
        } finally {
            withContext(Dispatchers.Main) { StrictMode.setThreadPolicy(original) }
        }
    }
}
