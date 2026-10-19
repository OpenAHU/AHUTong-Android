package com.ahu.ahutong.ui.plugin

import android.content.Context
import java.io.File

/**
 * 插件总开关（开发者选项）：关 = 全部门禁——卸载全部运行期插件并清空其存储。
 * 开关状态存独立 prefs，卸载连带 plugin_<id> 存储一起清，关闭即彻底退场。
 */
object AhupGate {

    private const val PREFS = "ahup_gate"
    private const val KEY_DEV_MODE = "dev_mode"

    fun isDevMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DEV_MODE, false)

    fun setDevMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEV_MODE, enabled).apply()
        if (!enabled) uninstallAll(context)
    }

    /** 卸载全部运行期插件 + 清空每个插件的隔离存储（KV + 二进制目录）。 */
    fun uninstallAll(context: Context) {
        AhupInstaller.installedIds(context).forEach { id ->
            AhupInstaller.uninstall(context, id)
            cleanupPluginData(context, id)
        }
        PluginRegistry.reload(context)
    }
}

/**
 * 卸载时插件的全部数据清场（KV 存储 + pluginFilesDir 二进制目录）。
 * 供 AhupGate.uninstallAll 与管理页单个卸载共用同一清理路径。
 */
internal fun cleanupPluginData(context: Context, pluginId: String) {
    // 插件通过 PluginContextWrapper 拿到的偏好文件名为 plugin_<id>__<name>，
    // 旧版则是单个 plugin_<id>.xml —— 两种前缀一起清，避免卸载后残留。
    val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
    prefsDir.listFiles()
        ?.filter { it.name.startsWith("plugin_${pluginId}") }
        ?.forEach { it.delete() }
    File(context.filesDir, "ahup_plugins/$pluginId").deleteRecursively()
}
