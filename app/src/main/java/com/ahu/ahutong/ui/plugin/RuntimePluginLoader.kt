package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.os.Build
import android.util.Log
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginIcon
import com.ahu.ahutong.core.plugin.PluginMeta
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 运行期插件装载：DexClassLoader 载入 .ahup 的 dex，实例化入口类。
 *
 * 类解析次序：插件自己的类 → 插件 dex；宿主类（Compose/设计系统/契约/okhttp）
 * → 父 ClassLoader（即宿主 APK）。插件 dex 只打自己的类，宿主侧类全部共享。
 */
object RuntimePluginLoader {

    /** 最近一次装载失败的「id: 原因」——给设置页/插件区排障展示（本机日志级别不可靠）。 */
    @Volatile var lastErrors: List<String> = emptyList()
        private set

    fun loadAll(context: Context): List<AhuPlugin> {
        val errors = mutableListOf<String>()
        val plugins = AhupInstaller.installedIds(context).mapNotNull { id ->
            runCatching { load(context, id) }
                .onFailure {
                    // 反射包装的异常剥到根因（InvocationTargetException 的 message 恒为 null）
                    val root = generateSequence(it) { e -> e.cause }.last()
                    errors.add("$id: ${root.javaClass.simpleName}: ${root.message}")
                    Log.e(TAG, "插件 $id 装载失败", it)
                }
                .getOrNull()
        }
        lastErrors = errors.toList()
        return plugins
    }

    private fun load(context: Context, id: String): AhuPlugin {
        val manifest = AhupInstaller.manifestOf(context, id)
            ?: throw AhupException("插件 $id 缺少 manifest")
        val dex = AhupInstaller.dexFile(context, id)
        // v2 包：按 SUPPORTED_ABIS 顺序取第一个存在的 lib/<abi>/ 目录作原生库搜索路径，
        // 插件内 System.loadLibrary("opencv_java4") / OpenCVLoader.initLocal() 才能找到库
        val libDir = AhupInstaller.nativeLibDir(context, id, Build.SUPPORTED_ABIS)
        val loader = DexClassLoader(
            dex.absolutePath,
            null, // 优化目录由系统托管（Android 8+ 推荐 null）
            libDir?.absolutePath,
            context.classLoader
        )
        val clazz = loader.loadClass(manifest.entryClass)
        val instance = clazz.getDeclaredConstructor().newInstance()
        require(instance is AhuPlugin) { "${manifest.entryClass} 未实现 AhuPlugin" }

        val iconBytes = AhupInstaller.iconFile(context, id, manifest.iconFile).readBytes()
        // 用包内图标与清单元数据覆写插件自述（图标在运行期包里没有 R 资源可用）
        return RuntimePluginWrapper(
            delegate = instance,
            metaOverride = manifest.toMeta(iconBytes)
        )
    }

    private fun AhupManifest.toMeta(iconBytes: ByteArray) = PluginMeta(
        id = id,
        title = title,
        summary = summary,
        icon = PluginIcon.Bytes(iconBytes),
        tint = tint,
        version = version,
        author = author
    )

    private const val TAG = "RuntimePluginLoader"
}

/** 运行期插件包装：代码用 dex 里的，入口元数据以包内 manifest 为准（图标走字节流）。 */
private class RuntimePluginWrapper(
    private val delegate: AhuPlugin,
    private val metaOverride: PluginMeta
) : AhuPlugin by delegate {
    override val meta: PluginMeta get() = metaOverride
}
