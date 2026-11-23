package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.network.BuildConfig
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * HTTP 日志拦截器的唯一构造点（约束见 AGENTS.md §2 R6）。
 *
 * 设计要点：
 * - release 构建返回 null —— 日志代码路径根本不存在，而不是依赖每个调用点"记得写 if (BuildConfig.DEBUG)"；
 * - 统一脱敏凭据类请求头，避免 token / cookie 进入 logcat 或崩溃上报；
 * - 调用方只依赖本类型，不再 import okhttp3.logging（由边界门禁强制）。
 */
object NetworkLogging {

    /** 只暴露项目真正用到的两个级别，避免调用点直接依赖 HttpLoggingInterceptor.Level。 */
    enum class Level {
        /** 只记录方法 / URL / 响应码。 */
        Basic,

        /** 记录请求与响应头（凭据头已脱敏）。 */
        Headers
    }

    fun debugInterceptor(level: Level = Level.Headers): Interceptor? {
        if (!BuildConfig.DEBUG) return null
        return HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Synjones-Auth")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            this.level = when (level) {
                Level.Basic -> HttpLoggingInterceptor.Level.BASIC
                Level.Headers -> HttpLoggingInterceptor.Level.HEADERS
            }
        }
    }
}
