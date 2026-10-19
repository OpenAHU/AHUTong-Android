package com.ahu.ahutong.ui.plugin

import android.content.Context
import com.ahu.ahutong.core.plugin.AhuPlugin
import com.ahu.ahutong.core.plugin.PluginBitmapConsumer
import com.ahu.ahutong.core.plugin.PluginCameraService
import com.ahu.ahutong.core.plugin.PluginCapability
import com.ahu.ahutong.core.plugin.PluginCapabilityDeniedException
import com.ahu.ahutong.core.plugin.PluginHostServices
import com.ahu.ahutong.core.plugin.PluginHttpClient
import com.ahu.ahutong.core.plugin.PluginHttpRequest
import com.ahu.ahutong.core.plugin.PluginHttpResponse
import com.ahu.ahutong.core.plugin.PluginLifecycle
import com.ahu.ahutong.core.plugin.PluginLifecycleObserver
import com.ahu.ahutong.core.plugin.PluginStorage
import com.ahu.ahutong.core.plugin.PluginThemeTokens
import com.ahu.ahutong.core.plugin.PluginUriCallback
import com.ahu.ahutong.data.network.AhuHttp
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 宿主能力实现（契约 v3）：插件拿到的一切都经过这里。
 *
 * - HTTP：AhuHttp.plain() 的裸客户端——无 CookieJar、无自动登录、无全局拦截器。
 * - 存储/文件：走 [PluginContextWrapper]（插件隔离目录 + `plugin_<id>__<name>` 偏好）。
 * - 相机：宿主持有 CAMERA 权限并统一处理运行时授权；未声明 CAMERA 调用即拒。
 * - 生命周期：宿主路由驱动 [dispatchStart] 等，插件只注册观察者。
 */
class PluginHostServicesImpl(
    override val pluginContext: Context,
    private val plugin: AhuPlugin,
    private val onClose: () -> Unit,
    private val themeTokensProvider: () -> PluginThemeTokens
) : PluginHostServices {

    private val httpClient: OkHttpClient by lazy {
        AhuHttp.plain().build()
    }

    private val pluginStorage: PluginStorage by lazy {
        SharedPrefsPluginStorage(pluginContext)
    }

    private val cameraService: PluginCameraService by lazy {
        PluginCameraServiceImpl(pluginContext)
    }

    private val lifecycleImpl = PluginLifecycleImpl()

    override fun http(): PluginHttpClient {
        ensure(PluginCapability.NETWORK)
        return object : PluginHttpClient {
            override fun execute(request: PluginHttpRequest): PluginHttpResponse {
                val body = request.body?.toRequestBody(JSON_MEDIA_TYPE)
                val builder = Request.Builder().url(request.url)
                request.headers.forEach { (k, v) -> builder.header(k, v) }
                val response = httpClient
                    .newBuilder()
                    .connectTimeout(request.connectTimeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(request.readTimeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(builder.method(request.method, body).build())
                    .execute()
                return response.use {
                    PluginHttpResponse(
                        code = it.code,
                        headers = it.headers.toMultimap(),
                        body = it.body?.bytes() ?: ByteArray(0)
                    )
                }
            }
        }
    }

    override fun storage(): PluginStorage {
        ensure(PluginCapability.PLUGIN_STORAGE)
        return pluginStorage
    }

    override fun filesDir(): File {
        ensure(PluginCapability.PLUGIN_STORAGE)
        return pluginContext.filesDir
    }

    override fun camera(): PluginCameraService {
        ensure(PluginCapability.CAMERA)
        return cameraService
    }

    override fun pickImage(callback: PluginUriCallback) {
        PluginHostLauncherHub.pickImage { uri -> callback.onResult(uri) }
    }

    override fun close() {
        onClose()
    }

    override val lifecycle: PluginLifecycle get() = lifecycleImpl

    override fun themeTokens(): PluginThemeTokens = themeTokensProvider()

    /* ---------------- 路由驱动 ---------------- */

    fun dispatchStart() = lifecycleImpl.dispatch { it.onStart() }
    fun dispatchResume() = lifecycleImpl.dispatch { it.onResume() }
    fun dispatchPause() = lifecycleImpl.dispatch { it.onPause() }
    fun dispatchStop() = lifecycleImpl.dispatch { it.onStop() }

    /** 返回 true 表示插件消费了返回键，宿主不再 pop。 */
    fun dispatchBack(): Boolean = lifecycleImpl.observers.any { it.onBackPressed() }

    private fun ensure(capability: PluginCapability) {
        if (capability !in plugin.capabilities) {
            throw PluginCapabilityDeniedException(capability, plugin.meta.id)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private class PluginLifecycleImpl : PluginLifecycle {
    val observers = LinkedHashSet<PluginLifecycleObserver>()

    override fun addObserver(observer: PluginLifecycleObserver) {
        observers.add(observer)
    }

    override fun removeObserver(observer: PluginLifecycleObserver) {
        observers.remove(observer)
    }

    fun dispatch(action: (PluginLifecycleObserver) -> Unit) {
        observers.toList().forEach { runCatching { action(it) } }
    }
}

/** 插件隔离 KV：文件由 PluginContextWrapper 命名空间化为 `plugin_<id>__storage`。 */
private class SharedPrefsPluginStorage(context: Context) : PluginStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    override fun getBoolean(key: String): Boolean? =
        if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "storage"
    }
}
