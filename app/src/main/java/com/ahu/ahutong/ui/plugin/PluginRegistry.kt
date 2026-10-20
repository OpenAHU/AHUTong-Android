package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.util.Log
import com.ahu.ahutong.core.plugin.AhuPlugin
import java.util.ServiceLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 插件注册表 = 编译期内置（ServiceLoader）+ 运行期安装（.ahup）的合并视图。
 *
 * 宿主对插件零静态引用：内置插件拔模块即消失；运行期插件卸载即消失。
 * [plugins] 是 StateFlow——安装/卸载后 UI 与路由自动重组。
 */
object PluginRegistry {

    private val _plugins = MutableStateFlow<List<AhuPlugin>>(emptyList())
    val plugins: StateFlow<List<AhuPlugin>> = _plugins.asStateFlow()

    private var initialized = false
    private val reloadMutex = Mutex()
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Application's Java entry point; discovery must not delay the first frame. */
    fun initAsync(context: Context) {
        val application = context.applicationContext
        initializationScope.launch { init(application) }
    }

    /** 进程内初始化一次（Main 启动时调用）。 */
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        reloadMutex.withLock {
            if (!initialized) reloadInternal(context)
        }
    }

    /** 安装/卸载后调用：重新发现全部插件。 */
    suspend fun reload(context: Context) = update(context) { }

    /** Keep discovery and installation from reading or removing the same files concurrently. */
    suspend fun update(context: Context, change: () -> Unit) = withContext(Dispatchers.IO) {
        reloadMutex.withLock {
            change()
            reloadInternal(context)
        }
    }

    private fun reloadInternal(context: Context) {
        val builtIn = runCatching {
            ServiceLoader.load(AhuPlugin::class.java, AhuPlugin::class.java.classLoader).toList()
        }.onFailure { Log.e(TAG, "内置插件发现失败", it) }
            .getOrDefault(emptyList())
        val runtime = RuntimePluginLoader.loadAll(context.applicationContext)
        _plugins.value = builtIn + runtime
        initialized = true
        Log.i(TAG, "插件注册表：内置 ${builtIn.size} 个，运行期 ${runtime.size} 个")
    }

    fun byId(id: String): AhuPlugin? = _plugins.value.firstOrNull { it.meta.id == id }

    private const val TAG = "PluginRegistry"
}
