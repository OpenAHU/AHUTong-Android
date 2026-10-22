package com.ahu.ahutong

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.security.SecureReadResult
import com.ahu.ahutong.data.security.SecureStorage
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.DefaultAhuSession
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live smoke tests; credentials are supplied as instrumentation arguments, never fixtures. */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceDeviceTest {
    @Test
    fun unreadableCiphertextIsPreservedAndCanBeReadAfterRecovery() {
        val key = "review.keystore.roundtrip"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("secure_storage_v1", android.content.Context.MODE_PRIVATE)
        try {
            SecureStorage.putString(key, "device-test-fixture")
            val encrypted = requireNotNull(preferences.getString(key, null))
            val malformed = "unsupported-version:invalid:invalid"
            assertTrue(preferences.edit().putString(key, malformed).commit())

            assertTrue(SecureStorage.readString(key) is SecureReadResult.Unavailable)
            assertEquals(malformed, preferences.getString(key, null))

            assertTrue(preferences.edit().putString(key, encrypted).commit())
            assertEquals("device-test-fixture", SecureStorage.getString(key))
        } finally {
            SecureStorage.remove(key)
        }
    }

    @Test
    fun signInAndPersistLiveAccount() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("review.live") == "true")
        val username = requireNotNull(args.getString("review.username"))
        val password = requireNotNull(args.getString("review.password"))
        val response = withTimeout(180_000) { DefaultAhuSession.signIn(username, password) }
        val outcome = response.valueOrNull()
        assertTrue("Live login did not establish an academic session", outcome is LoginOutcome.Success)
        SecureCredentialVault.saveWisdomPassword(password)
        AHUCache.saveCurrentUser((outcome as LoginOutcome.Success).user)
        assertTrue("The account must be safely persisted", SessionStore.currentUser()?.xh == username)
        assertTrue("The encrypted credential must be readable", SecureCredentialVault.wisdomPasswordForRefresh() == password)
    }

    @Test
    fun coldProcessRestoresPersistedAccount() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("review.live") == "true")
        val username = requireNotNull(args.getString("review.username"))
        assertTrue("A new process must restore the persisted identity", SessionStore.currentUser()?.xh == username)
        assertTrue("A new process must restore the encrypted credential", !SecureCredentialVault.wisdomPasswordForRefresh().isNullOrBlank())
    }

    @Test
    fun backgroundReturnAndActivityRecreationKeepTheAccount() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("review.live") == "true")
        val username = requireNotNull(args.getString("review.username"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            assertTrue("Activity lifecycle changes must retain the account", SessionStore.currentUser()?.xh == username)
            assertTrue("Lifecycle changes alone must not expire the session", AhuSessionState.status.value != AhuSessionState.Status.Expired)
        }
    }
}
