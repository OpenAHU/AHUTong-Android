package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.os.Build
import android.util.Log
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginIcon
import com.ahu.ahutong.core.plugin.PluginMeta
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行期插件装载（方案 A）。
 *
 * 每个插件一个 [PluginClassLoader]（契约 parent-only，其余 child-first）+ 一个独立的
 * [PluginResources] / [PluginContextWrapper]。插件 APK 里的资源、assets、原生库都指向它自己。
 *
 * v1/v2 包（裸 plugin.dex）不再支持：旧契约是 `@Composable Entry`，在新加载模型下无法渲染。
 */
object RuntimePluginLoader {

    /** 最近一次装载失败的「id: 原因」——给插件区排障展示（本机日志级别不可靠）。 */
    @Volatile
    var lastErrors: List<String> = emptyList()
        private set

    /** 已装载插件的上下文（路由渲染时要传给 createContentView）。 */
    private val pluginContexts = ConcurrentHashMap<String, PluginContextWrapper>()

    fun loadAll(context: Context): List<AhuPlugin> {
        val errors = mutableListOf<String>()
        pluginContexts.clear()
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

    /** 插件专属 Context；内置插件（无插件包）返回 null，由调用方回退到宿主 Context。 */
    fun contextFor(id: String): Context? = pluginContexts[id]

    private fun load(context: Context, id: String): AhuPlugin {
        val manifest = AhupInstaller.manifestOf(context, id)
            ?: throw AhupException("插件 $id 缺少 manifest")
        val apk = AhupInstaller.apkFile(context, id)
        if (!apk.isFile) {
            throw AhupException("插件 $id 是旧格式（v1/v2），当前版本不再支持，请用新版 SDK 重新打包")
        }

        // 按 SUPPORTED_ABIS 顺序取第一个存在的 lib/<abi>/ 目录；APK 内的 .so 由安装器解出
        val libDir = AhupInstaller.nativeLibDir(context, id, Build.SUPPORTED_ABIS)
        val classLoader = PluginClassLoader(
            apkPath = apk.absolutePath,
            nativeLibDir = libDir?.absolutePath,
            parent = context.classLoader
        )
        val resources = PluginResources.create(
            apkPath = apk.absolutePath,
            metrics = context.resources.displayMetrics,
            config = context.resources.configuration
        )
        val pluginContext = PluginContextWrapper(
            base = context.applicationContext,
            pluginId = id,
            pluginResources = resources,
            pluginClassLoader = classLoader
        )
        pluginContexts[id] = pluginContext

        val clazz = classLoader.loadClass(manifest.entryClass)
        val instance = clazz.getDeclaredConstructor().newInstance()
        if (instance !is AhuPlugin) {
            throw AhupException("${manifest.entryClass} 未实现 AhuPlugin")
        }

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

/** 运行期插件包装：代码用插件 APK 里的，入口元数据以包内 manifest 为准（图标走字节流）。 */
private class RuntimePluginWrapper(
    private val delegate: AhuPlugin,
    private val metaOverride: PluginMeta
) : AhuPlugin by delegate {
    override val meta: PluginMeta get() = metaOverride
}
