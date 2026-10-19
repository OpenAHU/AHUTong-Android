package com.ahu.ahutong.core.plugin

import android.content.Context
import android.view.View

/** 契约版本：插件 manifest 的 apiLevel 必须 <= 宿主常量。契约只增不减，删除/改签名必须递增。 */
const val PLUGIN_API_LEVEL = 1

/**
 * 安大通插件契约（v3，方案 A：插件静态自带全部依赖）。
 *
 * UI 边界是 **Android View**，不是 Composable：插件自带 Compose 时，`Composer` 会解析到插件
 * 自己的那一份，跨类加载器的 `@Composable` 方法不构成 override。View / Context / Bitmap 等
 * framework 类型由 boot classloader 加载，全局唯一，可以安全跨越。
 *
 * 同理，契约签名里**只允许**出现 JDK 类型、framework 类型、contract 自身类型——
 * 不得出现 Kotlin lambda（FunctionN）、`kotlin.Result`、协程、OkHttp、Compose、androidx.lifecycle。
 *
 * 生命周期：小工具页点击入口 → 宿主调用 [createContentView] 渲染；离开页面时调用 [onDestroy]。
 */
interface AhuPlugin {
    val meta: PluginMeta

    /** 声明需要的能力；宿主按声明提供，未声明的能力调用即抛 [PluginCapabilityDeniedException]。 */
    val capabilities: Set<PluginCapability>

    /**
     * 插件 UI 入口。返回一个**填满父容器**的 View（宿主用 `AndroidView` 承载）。
     *
     * @param context 插件的上下文：resources/assets/classLoader/数据目录都指向插件自己。
     * @param host 宿主能力入口（http/storage/相机/生命周期/主题 token）。
     *
     * 在主线程调用。insets 由插件自行处理（宿主该路由不消费 insets）。
     */
    fun createContentView(context: Context, host: PluginHostServices): View

    /** 宿主离开插件页时调用：插件必须在这里释放相机、取消协程、停止动画。 */
    fun onDestroy()
}
