package com.ahu.ahutong.feedback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The fixture contains synthetic data only and can be decrypted by the separately held key. */
@RunWith(AndroidJUnit4::class)
class FeedbackEncryptionDeviceTest {
    @Test fun installedPublicKeyEncryptsWithTheAndroidCryptoProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val publicPem = context.resources.openRawResource(R.raw.feedback_export_public_key)
            .bufferedReader().use { it.readText() }
        val encrypted = FeedbackLogEncryption.encrypt("AHUTong device encryption fixture\n合成日志 Cookie=synthetic-cookie", publicPem)
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("synthetic-cookie"))
        val destination = File(context.getExternalFilesDir(null), "feedback-validation.ahulog")
        destination.writeBytes(encrypted)
        assertTrue(destination.length() > 500)
    }
}
