package com.ahu.ahutong.core.plugin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * 宿主提供给插件的能力入口。
 *
 * 设计纪律：
 * - [pluginContext] 是插件专属 Context；**不再暴露宿主 appContext**（否则插件能读宿主 prefs/files）。
 * - [http] 必须是无全局会话的裸客户端（不带教务/一卡通/学习通的 Cookie 与拦截器）。
 * - [storage] / [filesDir] 按插件 id 隔离。
 * - 插件拿不到的东西就是故意拿不到的（学号、教务会话、其他插件的数据）。
 */
interface PluginHostServices {
    /** 插件专属 Context（资源/assets/类加载器/数据目录全部指向插件自己）。 */
    val pluginContext: Context

    /** 裸 HTTP 客户端。要求声明 [PluginCapability.NETWORK]。阻塞式，调用方必须在后台线程。 */
    fun http(): PluginHttpClient

    /** 插件隔离 KV 存储。要求声明 [PluginCapability.PLUGIN_STORAGE]。 */
    fun storage(): PluginStorage

    /** 插件私有二进制目录（大文件）。要求声明 [PluginCapability.PLUGIN_STORAGE]。 */
    fun filesDir(): File

    /** 相机服务。要求声明 [PluginCapability.CAMERA]。 */
    fun camera(): PluginCameraService

    /** Photo Picker（无需权限）。 */
    fun pickImage(callback: PluginUriCallback)

    /** 插件请求关闭自己：宿主退出插件页。 */
    fun close()

    /** 生命周期与返回键。 */
    val lifecycle: PluginLifecycle

    /** 主题 token（纯数据，自愿消费）。 */
    fun themeTokens(): PluginThemeTokens
}

/** 能力未声明时宿主的统一拒绝。 */
class PluginCapabilityDeniedException(capability: PluginCapability, pluginId: String) :
    IllegalStateException("插件 $pluginId 未声明能力 $capability")

/* ---------------- 回调：替代 lambda（FunctionN 跨不了类加载器） ---------------- */

fun interface PluginUriCallback {
    fun onResult(uri: Uri?)
}

fun interface PluginBooleanCallback {
    fun onResult(granted: Boolean)
}

interface PluginBitmapCallback {
    fun onSuccess(bitmap: Bitmap)
    fun onError(message: String)
}

fun interface PluginBitmapConsumer {
    fun onBitmap(bitmap: Bitmap)
}

/* ---------------- 生命周期 ---------------- */

interface PluginLifecycle {
    fun addObserver(observer: PluginLifecycleObserver)
    fun removeObserver(observer: PluginLifecycleObserver)
}

/**
 * 默认方法体只存在于契约侧（宿主），插件实现类可选择性覆盖。
 * 这要求全工程统一"不使用 -Xjvm-default 把默认体搬进接口"之外的任何方言。
 */
interface PluginLifecycleObserver {
    fun onStart() {}
    fun onResume() {}
    fun onPause() {}
    fun onStop() {}

    /** 返回 true 表示插件已消费返回键，宿主不再 pop。 */
    fun onBackPressed(): Boolean = false
}

/* ---------------- 主题 token ---------------- */

/** ARGB Long，纯数据，不依赖 Compose 类型。 */
data class PluginThemeTokens(
    val isDark: Boolean,
    val primary: Long,
    val onPrimary: Long,
    val background: Long,
    val surface: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val cornerRadiusDp: Float,
    val bodyFontSizeSp: Float
)
