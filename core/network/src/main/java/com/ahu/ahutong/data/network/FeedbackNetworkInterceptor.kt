package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ForwardingSink
import okio.ForwardingSource
import okio.GzipSource
import okio.buffer

/**
 * Captures an exchange into the private feedback ring, including headers applied by CookieJar.
 * Bodies are copied only while the real sender/consumer reads them; nothing is replayed or pre-read.
 */
internal class FeedbackNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host == "sentry.io" || original.url.host.endsWith(".sentry.io")) {
            return chain.proceed(original)
        }
        val generation = FeedbackDiagnostics.currentGeneration()
        val id = nextId.incrementAndGet()
        val started = System.nanoTime()
        val requestCapture = BodyCapture(original.body?.contentType(), original.header("Content-Encoding"))
        val body = original.body
        val request = if (body != null && !body.isDuplex() && requestCapture.isText) {
            original.newBuilder().method(original.method, CapturingRequestBody(body, requestCapture)).build()
        } else original

        val response = try {
            chain.proceed(request)
        } catch (error: Exception) {
            capture {
                FeedbackDiagnostics.recordNetwork(
                    "${original.method} ${original.url} 失败 (${error.javaClass.simpleName})",
                    "请求 #$id ${original.method} ${original.url}\n${renderHeaders(original.headers)}\n" +
                        requestDescription(body, requestCapture, complete = false) + "\n[请求失败：${error.javaClass.simpleName}]",
                    generation
                )
            }
            throw error
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        capture {
            FeedbackDiagnostics.recordNetwork(
                "${original.method} ${original.url} → ${response.code} (${elapsed}ms)",
                "请求 #$id ${original.method} ${original.url}\n${renderHeaders(original.headers)}\n" +
                    requestDescription(body, requestCapture) +
                    "\n\n响应 #$id ${response.code} (${elapsed}ms)\n${renderHeaders(response.headers)}\n" +
                    if (response.body == null) "[无响应正文]" else "[响应正文仅记录实际读取的部分]",
                generation
            )
        }
        val responseBody = response.body ?: return response
        val responseCapture = BodyCapture(responseBody.contentType(), response.header("Content-Encoding"))
        if (!responseCapture.isText) {
            capture {
                FeedbackDiagnostics.recordNetwork(
                    "请求 #$id 响应正文已省略",
                    "响应正文 #$id ${responseCapture.description(complete = false)}",
                    generation
                )
            }
            return response
        }
        return response.newBuilder().body(CapturingResponseBody(responseBody, responseCapture) { complete ->
            capture {
                FeedbackDiagnostics.recordNetwork(
                    "请求 #$id 响应正文${if (complete) "读取完成" else "未完整读取"}",
                    "响应正文 #$id\n${responseCapture.description(complete)}",
                    generation
                )
            }
        }).build()
    }

    private fun requestDescription(body: RequestBody?, capture: BodyCapture, complete: Boolean = true): String = when {
        body == null -> "[无请求正文]"
        body.isDuplex() -> "[双工请求正文已省略]"
        else -> capture.description(complete)
    }

    private class CapturingRequestBody(private val delegate: RequestBody, private val capture: BodyCapture) : RequestBody() {
        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun isOneShot(): Boolean = delegate.isOneShot()
        override fun isDuplex(): Boolean = delegate.isDuplex()
        override fun writeTo(sink: BufferedSink) {
            val tee = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    FeedbackNetworkInterceptor.capture { capture.append(source, byteCount) }
                    super.write(source, byteCount)
                }
            }.buffer()
            delegate.writeTo(tee)
            tee.emit() // Do not close the real network sink.
        }
    }

    private class CapturingResponseBody(
        private val delegate: ResponseBody,
        private val capture: BodyCapture,
        private val onFinished: (Boolean) -> Unit
    ) : ResponseBody() {
        private var finished = false
        private var observed = 0L
        private val capturedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val offset = sink.size
                    return try {
                        val count = super.read(sink, byteCount)
                        if (count > 0) {
                            observed += count
                            FeedbackNetworkInterceptor.capture { capture.append(sink, count, offset) }
                        } else if (count == -1L) finish(complete = true)
                        count
                    } catch (error: Exception) {
                        finish(complete = false)
                        throw error
                    }
                }

                override fun close() {
                    try { super.close() } finally {
                        val length = this@CapturingResponseBody.delegate.contentLength()
                        finish(complete = length >= 0 && observed >= length)
                    }
                }
            }.buffer()
        }
        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun source(): BufferedSource = capturedSource
        private fun finish(complete: Boolean) {
            if (!finished) {
                finished = true
                onFinished(complete)
            }
        }
    }

    private class BodyCapture(private val type: MediaType?, private val encoding: String?) {
        val isText: Boolean = type != null && (type.type == "text" ||
            listOf("json", "xml", "javascript", "x-www-form-urlencoded").any { type.subtype.contains(it) })
        private val bytes = Buffer()
        private var observed = 0L

        @Synchronized fun append(source: Buffer, byteCount: Long, offset: Long = 0) {
            observed += byteCount
            val remaining = (MAX_BODY_BYTES - bytes.size).coerceAtLeast(0)
            if (remaining > 0) source.copyTo(bytes, offset, minOf(remaining, byteCount))
        }

        @Synchronized fun description(complete: Boolean): String {
            if (!isText) return "[二进制或未声明文本类型的正文已省略]"
            if (encoding != null && !encoding.equals("identity", true) && !encoding.equals("gzip", true)) {
                return "[${encoding.take(32)} 编码的正文已省略]"
            }
            var truncated = observed > MAX_BODY_BYTES
            val bodyBytes = if (encoding.equals("gzip", true)) {
                if (!complete || truncated) return "[gzip 正文未完整读取或超出采集上限，已省略]"
                runCatching {
                    GzipSource(bytes.clone()).buffer().use { decoded ->
                        val output = Buffer()
                        var remaining = MAX_BODY_BYTES + 1
                        while (remaining > 0) {
                            val count = decoded.read(output, minOf(8192L, remaining))
                            if (count == -1L) break
                            remaining -= count
                        }
                        if (output.size > MAX_BODY_BYTES) truncated = true
                        output.readByteArray(minOf(output.size, MAX_BODY_BYTES))
                    }
                }.getOrElse { return "[gzip 正文无法解码，已省略]" }
            } else bytes.clone().readByteArray()
            val text = runCatching { bodyBytes.toString(type?.charset(Charsets.UTF_8) ?: Charsets.UTF_8) }
                .getOrDefault("[正文无法解码，已省略]")
            return DiagnosticSanitizer.passwords(text) +
                if (truncated) "\n[正文超过 256 KiB，已截断]" else if (!complete) "\n[正文未完整读取]" else ""
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 256L * 1024
        val nextId = AtomicLong()
        inline fun capture(block: () -> Unit) { runCatching(block) }

        // Headers.toString() masks credentials itself. Detailed feedback needs the real values;
        // the private ring applies password masking and the uploader requires explicit selection.
        fun renderHeaders(headers: Headers): String = buildString {
            for (index in 0 until headers.size) {
                append(headers.name(index)).append(": ").append(headers.value(index)).append('\n')
            }
        }
    }
}
