package com.ahu.ahutong.data.mail

import com.ahu.ahutong.data.network.AhuHttp
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MailBodyImage(val mimeType: String, val bytes: ByteArray)

/** Only opaque image routes in the inert email document call this loader. No WebView cookies enter it. */
class StudentMailBodyImages internal constructor(
    private val mailboxClient: OkHttpClient,
    private val sid: String,
    private val mailboxBase: HttpUrl,
    private val messageId: String,
    private val attachments: List<MailAttachment>,
    private val allowExternal: Boolean,
    private val checkCurrent: () -> Unit,
    private val externalClient: OkHttpClient,
    private val externalAllowed: (HttpUrl) -> Boolean = ::publicImageUrl,
    private val maxImageBytes: Int = 12 * 1024 * 1024,
    private val maxDocumentBytes: Long = 32L * 1024 * 1024,
    private val maxResources: Int = 40
) : Closeable {
    constructor(
        session: MailSession?,
        messageId: String,
        attachments: List<MailAttachment>,
        allowExternal: Boolean,
        checkCurrent: () -> Unit = {}
    ) : this(
        requireNotNull(session).client.newBuilder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build(),
        session.sid, "https://${StudentMailSession.MAIL_HOST}/".toHttpUrl(),
        messageId, attachments.toList(), allowExternal,
        { session.assertCurrent(); checkCurrent() }, anonymousClient()
    )

    private val lock = Any()
    private var closed = false
    private val calls = mutableSetOf<Call>()
    private val responses = mutableSetOf<Response>()
    private val resourceCount = AtomicInteger()
    private val documentBytes = AtomicLong()

    /** Blocking: called on WebView's resource worker, never the UI thread. Failures display a placeholder. */
    fun load(source: String): MailBodyImage? = try {
        ensureCurrent()
        val dataImage = source.startsWith("data:", true)
        if (source.length > (if (dataImage) MAX_DATA_ENCODED else 8192) || source.any(Char::isISOControl) ||
            resourceCount.incrementAndGet() > maxResources) null
        else if (dataImage) loadDataImage(source)
        else {
            val attachment = resolveAttachment(source)
            when {
                attachment != null -> loadInline(attachment)
                source.startsWith("cid:", true) -> null
                !allowExternal -> null
                else -> loadExternal(source)
            }
        }
    } catch (_: Exception) { null }

    private fun loadDataImage(source: String): MailBodyImage? {
        val comma = source.indexOf(',')
        if (comma !in 5..100) return null
        val descriptor = source.substring(5, comma).lowercase()
        if (!descriptor.endsWith(";base64")) return null
        val mime = descriptor.removeSuffix(";base64")
        if (mime !in IMAGE_TYPES) return null
        val bytes = runCatching { Base64.getDecoder().decode(source.substring(comma + 1)) }.getOrNull() ?: return null
        ensureCurrent()
        if (bytes.isEmpty() || bytes.size > minOf(maxImageBytes, MAX_DATA_DECODED) ||
            !imageSignatureMatches(mime, bytes) || documentBytes.addAndGet(bytes.size.toLong()) > maxDocumentBytes) return null
        ensureCurrent()
        return MailBodyImage(mime, bytes)
    }

    private fun loadInline(attachment: MailAttachment): MailBodyImage? {
        val part = attachment.partId ?: return null
        if (!part.matches(Regex("[0-9]+(?:\\.[0-9]+)*")) || messageId.isBlank() ||
            messageId.length > 512 || messageId.any(Char::isISOControl) ||
            attachment.messageId != messageId || !attachment.contentType.startsWith("image/", true)) return null
        val url = mailboxBase.newBuilder().addPathSegments("js6/s")
            .addQueryParameter("_host", StudentMailSession.MAIL_HOST)
            .addQueryParameter("func", "mbox:getMessageData")
            .addQueryParameter("sid", sid).addQueryParameter("mid", messageId)
            .addQueryParameter("part", part).addQueryParameter("mode", "inline")
            .addQueryParameter("_appName", "sirius-web").build()
        return request(mailboxClient, url) { response -> readImage(response) }
    }

    private fun resolveAttachment(source: String): MailAttachment? {
        val cid = if (source.startsWith("cid:", true)) normalizeContentId(source) else null
        attachments.firstOrNull { attachment ->
            attachment.messageId == messageId && attachment.partId != null &&
                ((cid != null && !attachment.contentId.isNullOrBlank() &&
                    normalizeContentId(attachment.contentId) == cid) ||
                    (!attachment.contentLocation.isNullOrBlank() && source == attachment.contentLocation))
        }?.let { return it }
        val url = source.toHttpUrlOrNull() ?: return null
        // Mail HTML may carry an obsolete SID: discard it and rebuild from the current session.
        if (url.host != StudentMailSession.MAIL_HOST || url.port !in setOf(80, 443) ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.encodedPath != "/js6/s" ||
            url.queryParameter("func") != "mbox:getMessageData" ||
            url.queryParameter("mid") != messageId) return null
        val part = url.queryParameter("part") ?: return null
        return attachments.firstOrNull { it.messageId == messageId && it.partId == part }
    }

    private fun loadExternal(source: String): MailBodyImage? {
        var url = source.toHttpUrlOrNull() ?: return null
        repeat(4) {
            ensureCurrent()
            if (url.scheme !in setOf("http", "https") || url.username.isNotEmpty() ||
                url.password.isNotEmpty() || !externalAllowed(url) ||
                url.host == StudentMailSession.MAIL_HOST) return null
            var target: HttpUrl? = null
            val image = request(externalClient, url) { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    target = response.header("Location")?.let(url::resolve)
                    null
                } else readImage(response)
            }
            if (image != null) return image
            url = target ?: return null
        }
        return null
    }

    private fun request(client: OkHttpClient, url: HttpUrl, consume: (Response) -> MailBodyImage?): MailBodyImage? {
        val call = client.newCall(Request.Builder().url(url).header("Accept", "image/*")
            .header("Cache-Control", "no-store").build())
        synchronized(lock) { ensureCurrent(); calls.add(call) }
        try {
            val response = call.execute()
            synchronized(lock) {
                if (closed) { response.close(); return null }
                responses.add(response)
            }
            try {
                return response.use { ensureCurrent(); consume(it).also { ensureCurrent() } }
            } finally { synchronized(lock) { responses.remove(response) } }
        } finally { synchronized(lock) { calls.remove(call) } }
    }

    private fun readImage(response: Response): MailBodyImage? {
        if (response.code != 200) return null
        val mime = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
        if (mime !in IMAGE_TYPES) return null
        val body = response.body ?: return null
        val declared = body.contentLength()
        if (declared > maxImageBytes) return null
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                ensureCurrent()
                val count = input.read(buffer)
                if (count < 0) break
                ensureCurrent()
                if (output.size().toLong() + count > maxImageBytes ||
                    documentBytes.addAndGet(count.toLong()) > maxDocumentBytes) return null
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        if ((declared >= 0 && declared != bytes.size.toLong()) || !imageSignatureMatches(mime, bytes)) return null
        ensureCurrent()
        return MailBodyImage(mime, bytes)
    }

    private fun ensureCurrent() {
        synchronized(lock) { check(!closed) }
        checkCurrent()
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            calls.forEach(Call::cancel)
            responses.forEach(Response::close)
            responses.clear()
            calls.clear()
        }
    }

    companion object {
        private const val MAX_DATA_DECODED = 2 * 1024 * 1024
        private const val MAX_DATA_ENCODED = 4 * ((MAX_DATA_DECODED + 2) / 3) + 100
        private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp",
            "image/x-icon", "image/vnd.microsoft.icon")

        private fun anonymousClient() = AhuHttp.plain(
            connectTimeoutSeconds = 15, readTimeoutSeconds = 20, callTimeoutSeconds = 30,
            followRedirects = false, followSslRedirects = false, retryOnConnectionFailure = false
        ).cookieJar(CookieJar.NO_COOKIES).cache(null).dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                    require(addresses.isNotEmpty() && addresses.all(::publicAddress))
                }
            }).build()

        private fun publicImageUrl(url: HttpUrl): Boolean = url.scheme in setOf("http", "https") &&
            url.username.isEmpty() && url.password.isEmpty() &&
            url.host != "localhost" && !url.host.endsWith(".localhost") &&
            !url.host.endsWith(".local") &&
            (url.host.none { it == ':' } && !url.host.matches(Regex("[0-9.]+")) ||
                runCatching { publicAddress(InetAddress.getByName(url.host)) }.getOrDefault(false))

        private fun publicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address
            if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
            if (bytes.size == 4 && (bytes[0].toInt() and 0xff) == 100 &&
                (bytes[1].toInt() and 0xff) in 64..127) return false
            return true
        }

        private fun normalizeContentId(value: String): String = runCatching {
            val trimmed = value.trim()
            URLDecoder.decode((if (trimmed.startsWith("cid:", true)) trimmed.substring(4) else trimmed)
                .replace("+", "%2B"), "UTF-8")
        }.getOrDefault(value).trim().removeSurrounding("<", ">")

        private fun imageSignatureMatches(mime: String, bytes: ByteArray): Boolean {
            fun prefix(vararg value: Int) = bytes.size >= value.size &&
                value.indices.all { (bytes[it].toInt() and 0xff) == value[it] }
            fun ascii(offset: Int, value: String) = bytes.size >= offset + value.length &&
                value.indices.all { (bytes[offset + it].toInt() and 0xff) == value[it].code }
            return when (mime) {
                "image/png" -> prefix(137, 80, 78, 71, 13, 10, 26, 10)
                "image/jpeg" -> prefix(255, 216, 255)
                "image/gif" -> ascii(0, "GIF87a") || ascii(0, "GIF89a")
                "image/webp" -> ascii(0, "RIFF") && ascii(8, "WEBP")
                "image/bmp" -> ascii(0, "BM")
                "image/x-icon", "image/vnd.microsoft.icon" -> prefix(0, 0, 1, 0)
                else -> false
            }
        }
    }
}
