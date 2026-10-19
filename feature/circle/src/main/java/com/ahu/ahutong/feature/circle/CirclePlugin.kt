package com.ahu.ahutong.feature.circle

import androidx.compose.runtime.Composable
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginCapability
import com.ahu.ahutong.core.plugin.PluginHostServices
import com.ahu.ahutong.core.plugin.PluginIcon
import com.ahu.ahutong.core.plugin.PluginMeta

/**
 * 校园圈子插件（只读）。
 *
 * ServiceLoader 发现要求：**public 无参构造**（不能用 Kotlin object）。
 * 服务索引见 src/main/resources/META-INF/services/。
 */
class CirclePlugin : AhuPlugin {

    override val meta = PluginMeta(
        id = "campus_circle",
        title = "校园圈子",
        summary = "安大 BBS 信息流与热榜（只读）",
        // 运行期装载时图标以包内 icon.png 为准（装载器覆写）；R 资源不进 dex，
        // 这里只能给占位值——运行期插件严禁引用 R（AAR 的 classes.jar 不含 R.class）
        icon = PluginIcon.Resource(0),
        tint = 0xFF5C6BC0, // 靛蓝，与现有小工具配色同族
        version = "0.2.0",
        author = "AHUTong"
    )

    override val capabilities = setOf(
        PluginCapability.NETWORK,
        PluginCapability.PLUGIN_STORAGE
    )

    /**
     * 契约 v3：UI 边界是 View。内置插件与宿主共用同一份 Compose（同一个类加载器），
     * 所以这里直接用 ComposeView 承载；运行期插件则会拿到自己的 Compose。
     */
    override fun createContentView(context: android.content.Context, host: PluginHostServices): android.view.View =
        androidx.compose.ui.platform.ComposeView(context).apply {
            setContent { CircleRoot(host) }
        }

    override fun onDestroy() {}
}
