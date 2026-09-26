package com.ahu.ahutong.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 非第一方客户端的唯一构造入口（约束见 docs/architecture/CONTEXT.md R8）。
 *
 * 为什么需要它：超时/重试/重定向策略原先在 9 个客户端里各写一遍，新增客户端时既容易漏配，
 * 也无法一眼看出某条链路的时间预期（探测 5 秒、下载 15 分钟）。
 *
 * 这里保留**参数**而不是预设常量：各链路的值本来就不同，收口的目标是"只有这一处构造"，
 * 而不是把数值强行统一（那会改变既有行为）。
 */
object AhuHttp {

    fun plain(
        connectTimeoutSeconds: Long = 10,
        readTimeoutSeconds: Long = 10,
        writeTimeoutSeconds: Long = 10,
        callTimeoutSeconds: Long? = null,
        retryOnConnectionFailure: Boolean = true,
        followRedirects: Boolean = true,
        followSslRedirects: Boolean = true
    ): OkHttpClient.Builder = OkHttpClient.Builder()
        .dns(AliyunDns)
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(retryOnConnectionFailure)
        .followRedirects(followRedirects)
        .followSslRedirects(followSslRedirects)
        .apply { callTimeoutSeconds?.let { callTimeout(it, TimeUnit.SECONDS) } }
}
