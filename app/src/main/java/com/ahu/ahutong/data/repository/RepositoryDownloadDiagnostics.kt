package com.ahu.ahutong.data.repository

import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

/** Metadata only: never reads a document body or records headers, URI grants, or signed queries. */
internal class RepositoryDownloadDiagnostics(kind: String, path: String) {
    private val generation = FeedbackDiagnostics.currentGeneration()
    private val id = sequence.incrementAndGet()
    private val started = System.nanoTime()

    init { event("start", "kind=$kind path=${path.take(600)}") }

    fun event(stage: String, detail: String = "") {
        runCatching {
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val record = safe("[资料下载 #$id] $stage elapsed=${elapsed}ms $detail").take(4_000)
            // Ordinary exports also contain these records; opting into network bodies is unnecessary.
            FeedbackDiagnostics.recordNetwork(record, record, generation)
        }
    }

    fun attempt(source: String, url: String) = event("attempt", "source=$source url=${safeUrl(url)}")

    fun response(source: String, response: Response) {
        val redirects = generateSequence(response.priorResponse) { it.priorResponse }.take(8)
            .joinToString(",") { "${it.code}@${it.request.url.host}" }
        event("response", "source=$source status=${response.code} url=${safeUrl(response.request.url.toString())} " +
            "type=${response.header("X-Download-Type").orEmpty().take(40)} " +
            "contentType=${response.body?.contentType()} length=${byteCount(response.body?.contentLength() ?: -1)} " +
            "fileSize=${byteCount(response.header("X-File-Size")?.toLongOrNull() ?: -1)} redirects=[$redirects]")
    }

    fun complete(detail: String = "") = event("complete", detail)

    // Grouping prevents the ordinary-log account-number sanitizer from hiding large byte counts.
    fun byteCount(value: Long): String = String.format(Locale.ROOT, "%,d", value)

    fun failure(stage: String, error: Throwable) {
        val causes = ArrayDeque<Pair<String, Throwable>>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        causes.add("error" to error)
        val details = mutableListOf<String>()
        while (causes.isNotEmpty() && details.size < 10) {
            val (relation, current) = causes.removeFirst()
            if (!seen.add(current)) continue
            details += "$relation=${current.javaClass.simpleName}:${safe(current.message.orEmpty()).take(300)}"
            current.cause?.let { causes.add("cause" to it) }
            current.suppressed.take(6).forEach { causes.add("suppressed" to it) }
        }
        val eventType = when {
            error is CancellationException -> "cancelled"
            stage.startsWith("attempt:") -> "attempt-failed"
            else -> "failed"
        }
        event(eventType, "stage=$stage ${details.joinToString(" | ")}")
    }

    private fun safeUrl(value: String): String = value.toHttpUrlOrNull()?.let {
        "${it.scheme}://${it.host}:${it.port}"
    } ?: "[invalid URL]"

    private fun safe(value: String): String = DiagnosticSanitizer.safe(
        httpUrl.replace(localUri.replace(value.take(12_000), "[local URI]")) { safeUrl(it.value) }
    ).replace('\n', ' ').replace('\r', ' ')

    private companion object {
        val sequence = AtomicLong()
        val localUri = Regex("""(?i)\b(?:content|file)://[^\s<>"']+""")
        val httpUrl = Regex("""(?i)\bhttps?://[^\s<>"']+""")
    }
}
