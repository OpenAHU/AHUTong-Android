package com.ahu.ahutong.ui.state

import com.ahu.ahutong.testing.FakeAppDataReset
import com.ahu.ahutong.testing.FakeAppUpdateGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * 设置枢纽页的两个动作：清除所有数据、取更新说明。
 *
 * 它们此前只活在 Compose 的回调与 LaunchedEffect 里——9 步复位只能靠真机点一次验证，
 * 更新说明的失败/空文案也只能靠断网才能看到。现在两者都由 fake 驱动。
 */
class SettingsViewModelTest {

    private fun viewModel(
        reset: FakeAppDataReset = FakeAppDataReset(),
        updates: FakeAppUpdateGateway = FakeAppUpdateGateway()
    ) = SettingsViewModel(reset, updates)

    @Test
    fun `clearing all data asks the reset port exactly once`() = runBlocking {
        val reset = FakeAppDataReset()
        val subject = viewModel(reset = reset)

        subject.clearAllData()

        assertEquals(1, reset.clearCount)
    }

    @Test
    fun `a blank changelog becomes the empty-state wording`() = runBlocking {
        val subject = viewModel(updates = FakeAppUpdateGateway(changelogText = "   "))

        assertEquals("暂无更新说明", subject.changelog())
    }

    @Test
    fun `a failing changelog becomes the failure wording`() = runBlocking {
        val subject = viewModel(updates = FakeAppUpdateGateway(failChangelog = true))

        assertEquals("获取失败", subject.changelog())
    }

}

