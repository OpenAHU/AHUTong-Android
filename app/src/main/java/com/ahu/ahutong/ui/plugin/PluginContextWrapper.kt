package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 交给插件的 Context：资源 / assets / 类加载器 / 数据目录全部指向插件自己。
 *
 * 与宿主隔离的边界：
 * - 数据目录 → `filesDir/ahup_plugins/<id>/`（与现有 [cleanupPluginData] 的清理路径一致）
 * - SharedPreferences → `plugin_<id>__<name>` 命名空间
 * - 外部存储 → 一律 null
 * - `getApplicationContext()` 返回自身，避免插件借道拿到宿主 Context
 */
internal class PluginContextWrapper(
    base: Context,
    val pluginId: String,
    private val pluginResources: Resources,
    private val pluginClassLoader: ClassLoader
) : ContextWrapper(base) {

    private val rootDir: File by lazy {
        File(base.filesDir, "ahup_plugins/$pluginId").apply { mkdirs() }
    }

    override fun getApplicationContext(): Context = this

    override fun getResources(): Resources = pluginResources

    override fun getAssets() = pluginResources.assets

    override fun getClassLoader(): ClassLoader = pluginClassLoader

    override fun getFilesDir(): File = dir("files")

    override fun getCacheDir(): File = dir("cache")

    override fun getNoBackupFilesDir(): File = dir("no_backup")

    override fun getCodeCacheDir(): File = dir("code_cache")

    override fun getDir(name: String, mode: Int): File = dir("app_$name")

    override fun getDatabasePath(name: String): File {
        val databases = dir("databases")
        return File(databases, name)
    }

    override fun getSharedPreferences(name: String, mode: Int) =
        baseContext.getSharedPreferences("plugin_${pluginId}__$name", mode)

    override fun getExternalFilesDir(type: String?): File? = null

    override fun getExternalCacheDir(): File? = null

    override fun openFileOutput(name: String, mode: Int): FileOutputStream =
        FileOutputStream(file("files", name), (mode and Context.MODE_APPEND) != 0)

    override fun openFileInput(name: String): FileInputStream =
        FileInputStream(file("files", name))

    override fun deleteFile(name: String): Boolean {
        val f = file("files", name)
        return !f.exists() || f.delete()
    }

    override fun fileList(): Array<String> = dir("files").list() ?: emptyArray()

    private fun dir(name: String): File = File(rootDir, name).apply { mkdirs() }

    private fun file(dirName: String, name: String): File = File(dir(dirName), name)
}
