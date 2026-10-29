package com.ahu.ahutong.core.common.diagnostics

import java.time.Instant
import java.util.ArrayDeque

/** Always-on, bounded process memory shared by feedback and automatic crash diagnostics. */
object FeedbackDiagnostics {
    private val buffer = DiagnosticBuffer()
    @Volatile private var onClearListener: (() -> Unit)? = null
    private val deliveryLock = Any()
    private var recordListener: ((DiagnosticCategory, String) -> Unit)? = null

    fun currentGeneration(): Long = buffer.currentGeneration()

    fun recordRuntime(message: String) = append(DiagnosticCategory.RUNTIME, message, currentGeneration())

    fun recordError(message: String) = append(DiagnosticCategory.ERROR, message, currentGeneration())

    fun recordNative(generation: Long, level: Int, target: String, message: String) {
        buffer.appendNative(generation, level, target, message) { category, summary ->
            publish(category, summary, generation)
        }
    }

    fun recordNetwork(summary: String, details: String, generation: Long = currentGeneration()) {
        append(DiagnosticCategory.RUNTIME, summary, generation)
        append(DiagnosticCategory.NETWORK, details, generation)
    }

    fun snapshot(category: DiagnosticCategory): String = buffer.snapshot(category)

    fun snapshot(category: DiagnosticCategory, maxChars: Int): String = buffer.snapshot(category, maxChars)

    /** Only ordinary, redacted records are delivered; raw network details never leave the ring here. */
    fun setRecordListener(listener: ((DiagnosticCategory, String) -> Unit)?) = synchronized(deliveryLock) {
        recordListener = listener
    }

    private fun append(category: DiagnosticCategory, message: String, generation: Long) {
        buffer.append(category, message, generation)
        publish(category, message, generation)
    }

    private fun publish(category: DiagnosticCategory, message: String, generation: Long) {
        if (category == DiagnosticCategory.NETWORK) return
        val summary = DiagnosticSanitizer.safe(message).take(2_000)
        synchronized(deliveryLock) {
            if (generation == currentGeneration()) runCatching { recordListener?.invoke(category, summary) }
        }
    }

    /** Invalidates in-flight captures, too, so a prior account cannot repopulate the new account's ring. */
    fun setOnClearListener(listener: (() -> Unit)?) { onClearListener = listener }

    fun clear() = synchronized(deliveryLock) {
        buffer.clear()
        DiagnosticSanitizer.clearKnownPasswords()
        runCatching { onClearListener?.invoke() }
        Unit
    }
}

internal class DiagnosticBuffer(
    private val maxBytes: Int = 5 * 1024 * 1024,
    private val maxAgeMillis: Long = 10 * 60 * 1000,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallMillis: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val category: DiagnosticCategory, val elapsed: Long, val text: String, val bytes: Int)
    private val entries = ArrayDeque<Entry>()
    private var bytes = 0
    private var generation = 0L

    @Synchronized fun currentGeneration(): Long = generation

    fun appendNative(
        expectedGeneration: Long, level: Int, target: String, message: String,
        onSummary: (DiagnosticCategory, String) -> Unit = { _, _ -> }
    ) {
        if (level !in 1..5 || expectedGeneration != currentGeneration()) return
        if (target != "ahutong_rs" && !target.startsWith("ahutong_rs::") &&
            target != "guixu" && !target.startsWith("guixu::")) return
        val severity = listOf("ERROR", "WARN", "INFO", "DEBUG", "TRACE")[level - 1]
        val prefix = "[Rust $severity ${target.take(160)}] "
        // An oversized fragment could end in half a password. Omit it rather than retaining that fragment.
        val boundedMessage = if (message.toByteArray(Charsets.UTF_8).size > 256 * 1024)
            "[Rust日志超过256KiB，正文未采集]" else message
        val category = if (level <= 2) DiagnosticCategory.ERROR else DiagnosticCategory.RUNTIME
        val summary = prefix + nativeSummary(boundedMessage)
        append(category, summary, expectedGeneration)
        onSummary(category, summary)
        append(DiagnosticCategory.NETWORK, prefix + boundedMessage, expectedGeneration)
    }

    fun append(category: DiagnosticCategory, message: String, expectedGeneration: Long) {
        val sanitized = if (category == DiagnosticCategory.NETWORK) DiagnosticSanitizer.passwords(message)
            else DiagnosticSanitizer.safe(message)
        val text = boundedUtf8("${Instant.ofEpochMilli(wallMillis())} $sanitized", (maxBytes - 2).coerceAtLeast(0))
        val entryBytes = text.toByteArray(Charsets.UTF_8).size + 2
        synchronized(this) {
            if (generation != expectedGeneration) return
            val now = elapsedMillis()
            prune(now)
            while (entries.isNotEmpty() && bytes + entryBytes > maxBytes) removeFirst()
            entries.addLast(Entry(category, now, text, entryBytes))
            bytes += entryBytes
        }
    }

    @Synchronized fun snapshot(category: DiagnosticCategory, maxChars: Int = maxBytes): String {
        require(maxChars >= 0)
        prune(elapsedMillis())
        // Select recent records before joining: a crash must not allocate another full 5 MiB ring.
        val recent = ArrayDeque<String>()
        var remaining = maxChars
        val iterator = entries.descendingIterator()
        while (iterator.hasNext() && remaining > 0) {
            val entry = iterator.next()
            if (entry.category != category) continue
            val text = entry.text
            if (text.length > remaining) {
                recent.addFirst(text.take(remaining))
                break
            }
            recent.addFirst(text)
            remaining -= text.length + 2
        }
        val text = recent.joinToString("\n\n")
        return if (category == DiagnosticCategory.NETWORK) DiagnosticSanitizer.passwords(text)
            else DiagnosticSanitizer.safe(text)
    }

    @Synchronized fun clear() {
        entries.clear()
        bytes = 0
        generation++
    }

    private fun prune(now: Long) {
        while (entries.isNotEmpty() && (now < entries.first.elapsed || now - entries.first.elapsed >= maxAgeMillis)) {
            removeFirst()
        }
    }

    private fun removeFirst() { bytes -= entries.removeFirst().bytes }

    private fun nativeSummary(message: String): String {
        // Native parsers sometimes log whole pages/JSON. Those payloads belong only to the opt-in details.
        val payloadStart = nativePayload.find(message)?.range?.first ?: message.length
        return message.take(payloadStart) + if (payloadStart < message.length) "[正文见网络详情]" else ""
    }

    private val nativePayload = Regex(
        """(?i)<[!/?a-z][^>]*>|\{|\[\s*(?:\{|["'])|\b(?:html(?:\s+(?:content|head))?|(?:server\s+error|response)\s+body|script\s+content\s+head|(?:course\s+)?json)\s*[:=]"""
    )

    private fun boundedUtf8(text: String, limit: Int): String {
        val encoded = text.toByteArray(Charsets.UTF_8)
        if (encoded.size <= limit) return text
        val suffix = "\n[记录过长，已截断]"
        val suffixBytes = suffix.toByteArray(Charsets.UTF_8)
        if (limit <= suffixBytes.size) return "" // Test-only limits smaller than the truncation marker.
        return encoded.copyOf((limit - suffixBytes.size - 3).coerceAtLeast(0)).toString(Charsets.UTF_8) + suffix
    }
}
