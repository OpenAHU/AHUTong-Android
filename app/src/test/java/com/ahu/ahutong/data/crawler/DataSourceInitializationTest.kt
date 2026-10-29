package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.data.AHURepository
import kotlin.test.Test
import kotlin.test.assertNotNull

class DataSourceInitializationTest {
    @Test
    fun `creating production data sources does not open encrypted cookie storage`() {
        // No Application environment or Android Keystore is installed in this JVM.
        // These constructors run during Application.onCreate on the main thread.
        assertNotNull(SdkDataSource())
        AHURepository.initializeDataSource(useMock = false)
    }
}
