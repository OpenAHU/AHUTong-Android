package com.ahu.ahutong.ui.plugin

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics

/**
 * 从插件 APK 构造独立的 `Resources`（插件自带的 res/ + resources.arsc + assets/）。
 *
 * 依赖隐藏 API `AssetManager.addAssetPath(String)`，Android 9+ 受 hidden API 限制。
 * 该调用失败时必须由调用方降级为可读错误（安装期 E_RESOURCES），不要让插件加载崩溃。
 */
internal object PluginResources {

    class BuildException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * 用宿主的 metrics/config 构造插件 Resources。
     * @throws BuildException 隐藏 API 不可用或 APK 内无资源表时。
     */
    fun create(apkPath: String, metrics: DisplayMetrics, config: Configuration): Resources {
        val assetManager = try {
            newAssetManager()
        } catch (e: Throwable) {
            throw BuildException("无法创建 AssetManager：${e.javaClass.simpleName}", e)
        }

        try {
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val cookie = addAssetPath.invoke(assetManager, apkPath) as? Int ?: 0
            if (cookie == 0) {
                throw BuildException("addAssetPath 返回 0（APK 内没有资源表）")
            }
        } catch (e: BuildException) {
            throw e
        } catch (e: Throwable) {
            throw BuildException(
                "插件资源加载失败（AssetManager.addAssetPath 不可用）：${e.javaClass.simpleName}",
                e
            )
        }

        return try {
            @Suppress("DEPRECATION")
            Resources(assetManager, metrics, config)
        } catch (e: Throwable) {
            throw BuildException("构造插件 Resources 失败：${e.javaClass.simpleName}", e)
        }
    }

    private fun newAssetManager(): AssetManager =
        AssetManager::class.java.getDeclaredConstructor().newInstance()
}
