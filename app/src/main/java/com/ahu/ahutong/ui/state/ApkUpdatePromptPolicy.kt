package com.ahu.ahutong.ui.state

/** Counts only foreground time before the first forced download. */
internal class ApkUpdateForegroundCountdown(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    var remainingMillis: Long? = null
        private set
    private var resumedAt: Long? = null

    fun reset(delayMillis: Long?) {
        remainingMillis = delayMillis
        resumedAt = null
    }

    fun resume(): Long? {
        val remaining = remainingMillis ?: return null
        resumedAt = nowMillis()
        return remaining
    }

    fun pause() {
        val started = resumedAt ?: return
        remainingMillis = remainingMillis?.let { (it - (nowMillis() - started).coerceAtLeast(0)).coerceAtLeast(0) }
        resumedAt = null
    }
}

/** Downloader currently publishes messages rather than typed errors; retry only transient failures. */
internal fun canAutomaticallyRetryApkDownload(message: String): Boolean {
    val lower = message.lowercase()
    if (listOf("校验", "sha", "checksum", "digest", "不受信任", "地址无效", "地址不", "大小信息无效",
            "过大", "大小限制", "声明大小", "重定向", "无法替换", "permission", "no space", "空间不足")
            .any(lower::contains)
    ) return false
    Regex("""http\s+(\d{3})""", RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)?.toIntOrNull()?.let {
        return it == 408 || it == 425 || it == 429 || it in 500..599
    }
    return lower == "下载失败" || listOf("timeout", "timed out", "connect", "network", "resolve host",
        "unknownhost", "socket", "reset", "unexpected end of stream", "broken pipe",
        "网络", "超时", "下载不完整", "下载内容为空", "连接")
        .any(lower::contains)
}
