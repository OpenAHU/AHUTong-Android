package com.ahu.ahutong

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityRecreationTest {
    @Test
    fun successfulLoginDiscardsPreviousWalletStateAndItsViewModelScope() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previousActivity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        ) as MainActivity
        lateinit var previousWallet: DiscoveryViewModel
        instrumentation.runOnMainSync {
            previousWallet = ViewModelProvider(previousActivity)[DiscoveryViewModel::class.java]
            previousWallet.balance = PREVIOUS_ACCOUNT_BALANCE
            previousWallet.transitionBalance = PREVIOUS_ACCOUNT_BALANCE
            previousWallet.bathroom[PREVIOUS_ACCOUNT_ROOM] = "open"
        }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var replacement: MainActivity? = null
        try {
            instrumentation.runOnMainSync { previousActivity.restartMainAfterLogin() }
            replacement = instrumentation.waitForMonitorWithTimeout(monitor, 10_000L) as? MainActivity
                ?: error("Successful login did not open a new MainActivity")
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val nextWallet = ViewModelProvider(checkNotNull(replacement))[DiscoveryViewModel::class.java]
                assertNotSame(previousActivity, replacement)
                assertNotSame(previousWallet, nextWallet)
                assertFalse(nextWallet.balance == PREVIOUS_ACCOUNT_BALANCE)
                assertFalse(nextWallet.transitionBalance == PREVIOUS_ACCOUNT_BALANCE)
                assertFalse(nextWallet.bathroom.containsKey(PREVIOUS_ACCOUNT_ROOM))
                assertTrue(previousActivity.isDestroyed)
                assertTrue(previousWallet.viewModelScope.coroutineContext[Job]?.isCancelled == true)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync {
                replacement?.finish()
                if (!previousActivity.isDestroyed) previousActivity.finish()
            }
        }
    }

    private companion object {
        const val PREVIOUS_ACCOUNT_BALANCE = 987_654_321.0
        const val PREVIOUS_ACCOUNT_ROOM = "previous-account-only-test-room"
    }
}
