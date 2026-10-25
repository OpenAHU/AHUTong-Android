package com.ahu.ahutong.ui.plugin

import android.content.Context
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginCapability
import com.ahu.ahutong.core.plugin.PluginCapabilityDeniedException
import com.ahu.ahutong.core.plugin.PluginMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 测试生产宿主的能力门控、关闭回调和签名身份绑定。
 * 不渲染插件 UI，也不通过 Android stub 推断文件系统隔离。
 */
class PluginCameraHostGateTest {

    private class FakePlugin(
        private val declared: Set<PluginCapability>,
        override val meta: PluginMeta
    ) : AhuPlugin {
        override val capabilities: Set<PluginCapability> get() = declared

        /** 门控测试不渲染 UI，调用即抛（测试不会渲染）。 */
        override fun createContentView(
            context: android.content.Context,
            host: com.ahu.ahutong.core.plugin.PluginHostServices
        ): android.view.View = throw UnsupportedOperationException("门控测试不渲染 UI")

        override fun onDestroy() {}
    }

    private fun fakeMeta(id: String) = PluginMeta(
        id = id,
        title = id,
        summary = "",
        icon = com.ahu.ahutong.core.plugin.PluginIcon.Bytes(ByteArray(0)),
        tint = 0L,
        version = "0",
        author = "test"
    )

    /** JVM 单测里的 Context stub：ContextWrapper 是具体类，mockable android.jar
     *  （isReturnDefaultValues=true）下方法全为默认实现，构造传 null 即可。 */
    private val fakeContext: Context by lazy {
        android.content.ContextWrapper(null)
    }

    private fun hostOf(plugin: AhuPlugin, onClose: () -> Unit = {}): PluginHostServicesImpl =
        PluginHostServicesImpl(
            pluginContext = fakeContext,
            plugin = plugin,
            onClose = onClose,
            themeTokensProvider = {
                com.ahu.ahutong.core.plugin.PluginThemeTokens(
                    isDark = false,
                    primary = 0L,
                    onPrimary = 0L,
                    background = 0L,
                    surface = 0L,
                    onSurface = 0L,
                    onSurfaceVariant = 0L,
                    cornerRadiusDp = 0f,
                    bodyFontSizeSp = 0f
                )
            }
        )

    @Test
    fun cameraRequiresDeclaredCapability() {
        val host = hostOf(
            FakePlugin(setOf(PluginCapability.NETWORK, PluginCapability.PLUGIN_STORAGE), fakeMeta("p1"))
        )
        assertFailsWith<PluginCapabilityDeniedException> { host.camera() }
    }

    @Test
    fun pluginFilesDirRequiresStorageCapability() {
        val host = hostOf(FakePlugin(setOf(PluginCapability.NETWORK), fakeMeta("p1")))
        assertFailsWith<PluginCapabilityDeniedException> { host.filesDir() }
    }

    @Test
    fun signaturePayloadV3BindsContentAndIdentity() {
        val apk = ByteArray(16) { 1 }
        val icon = ByteArray(8) { 2 }
        val payload = signaturePayloadV3(apk, icon, "p1", "1.0.0", "com.x.P")
        val expected = apk + icon + "p1\n1.0.0\ncom.x.P".toByteArray(Charsets.UTF_8)
        assertTrue(payload.contentEquals(expected), "v3 载荷 = apk + icon + id/version/entryClass")

        val otherId = signaturePayloadV3(apk, icon, "p2", "1.0.0", "com.x.P")
        assertTrue(!payload.contentEquals(otherId), "改 id 必须改变载荷（防覆盖他人插件）")

        val otherVersion = signaturePayloadV3(apk, icon, "p1", "1.0.1", "com.x.P")
        assertTrue(!payload.contentEquals(otherVersion), "改版本必须改变载荷")
    }

    @Test
    fun closeInvokesHostCallbackWithoutCapabilityGate() {
        // 无任何能力声明的插件也能关闭自己——close 不属于任何能力门控
        var invoked = 0
        val host = hostOf(FakePlugin(emptySet(), fakeMeta("p1")), onClose = { invoked++ })
        host.close()
        assertEquals(1, invoked, "close() 必须触发宿主 onClose 回调（popBackStack）")
        host.close()
        assertEquals(2, invoked)
    }

}
