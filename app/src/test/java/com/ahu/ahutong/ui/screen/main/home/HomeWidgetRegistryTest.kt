package com.ahu.ahutong.ui.screen.main.home

import android.content.Context
import android.view.View
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginCapability
import com.ahu.ahutong.core.plugin.PluginHostServices
import com.ahu.ahutong.core.plugin.PluginIcon
import com.ahu.ahutong.core.plugin.PluginMeta
import com.ahu.ahutong.ui.plugin.PluginRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class HomeWidgetRegistryTest {
    @Test fun pluginIndexTracksDiscoveryAndRemovalWithoutRebuildingOnEveryRead() {
        // Substitute discovery output without loading a Dex plugin or touching disk.
        val field = PluginRegistry::class.java.getDeclaredField("_plugins").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val discovery = field.get(null) as MutableStateFlow<List<AhuPlugin>>
        val original = discovery.value
        try {
            discovery.value = emptyList()
            assertFalse(HomeWidgetRegistry.widgetById.containsKey("plugin:test_widget"))
            val plugin = object : AhuPlugin {
                override val meta = PluginMeta("test_widget", "Test", "", PluginIcon.Resource(0), 0L, "1", "test")
                override val capabilities: Set<PluginCapability> = emptySet()
                override fun createContentView(context: Context, host: PluginHostServices): View =
                    View(context)
                override fun onDestroy() = Unit
            }
            discovery.value = listOf(plugin)
            val index = HomeWidgetRegistry.widgetById
            assertEquals("Test", index["plugin:test_widget"]?.title)
            assertSame(index, HomeWidgetRegistry.widgetById)
            discovery.value = emptyList()
            assertFalse(HomeWidgetRegistry.widgetById.containsKey("plugin:test_widget"))
        } finally { discovery.value = original }
    }
}
