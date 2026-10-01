package com.ahu.ahutong.data.mail

import com.google.gson.JsonParser
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Downloads only the mailbox message part endpoint observed in the attachment HAR. */
class StudentMailDownload internal constructor(
    client: OkHttpClient,
    private val sid: String,
    private val baseUrl: HttpUrl,
    private val guard: () -> Unit
) {
    constructor(session: MailSession) : this(
        session.client, session.sid, mailboxUrl(session.host), session::assertCurrent
    )

    private val client = client.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES).build()

    /** The caller owns [output], including closing it and deleting a partial file on failure. */
    suspend fun download(
        messageId: String,
        partId: String,
        output: OutputStream,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): Long {
        guard()
        require(messageId.isNotBlank() && messageId.length <= 512 && !messageId.any(Char::isISOControl))
        require(partId.matches(Regex("[0-9]+(?:\\.[0-9]+)*")))
        val url = baseUrl.newBuilder().addPathSegments("js6/s")
            .addQueryParameter("_host", StudentMailSession.MAIL_HOST)
            .addQueryParameter("func", "mbox:getMessageData")
            .addQueryParameter("sid", sid)
            .addQueryParameter("mode", "download")
            .addQueryParameter("part", partId)
            .addQueryParameter("mid", messageId)
            .addQueryParameter("_appName", "sirius-web")
            .addQueryParameter("trigger_type", "user_click")
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "*/*")
            .header("Referer", "https://mail.stu.ahu.edu.cn/static/sirius-web/")
            .build()
        // Reading on OkHttp's worker keeps cancellation attached to the socket for the entire body.
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            val activeResponse = AtomicReference<Response?>()
            continuation.invokeOnCancellation {
                call.cancel()
                activeResponse.getAndSet(null)?.close()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        MailServiceFailure("附件下载失败，请检查网络后重试")
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    activeResponse.set(response)
                    if (!continuation.isActive) {
                        activeResponse.getAndSet(null)?.close()
                        return
                    }
                    try {
                        val written = response.use { reply ->
                            guard()
                            validateResponse(reply)
                            val body = reply.body ?: throw MailServiceFailure("邮箱没有返回附件数据")
                            val total = body.contentLength().takeIf { it >= 0 }
                            var downloaded = 0L
                            onProgress(downloaded, total)
                            body.byteStream().use inputBody@{ input ->
                                val buffer = ByteArray(32 * 1024)
                                while (continuation.isActive) {
                                    guard()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (!continuation.isActive) return@inputBody
                                    guard()
                                    output.write(buffer, 0, count)
                                    downloaded += count
                                    onProgress(downloaded, total)
                                }
                            }
                            if (continuation.isActive) {
                                guard()
                                if (total != null && downloaded != total) {
                                    throw MailServiceFailure("附件未下载完整，请重新下载")
                                }
                                output.flush()
                                guard()
                            }
                            downloaded
                        }
                        if (continuation.isActive) continuation.resume(written)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            when (error) {
                                is MailSessionExpired, is MailServiceFailure -> error
                                else -> MailServiceFailure("附件下载失败，请检查网络和保存位置后重试")
                            }
                        )
                    } finally {
                        activeResponse.getAndSet(null)?.close()
                    }
                }
            })
        }
    }

    private fun validateResponse(response: Response) {
        if (response.code in setOf(301, 302, 303, 307, 308, 401, 403)) throw MailSessionExpired()
        if (response.code != 200) throw MailServiceFailure("附件下载失败（HTTP ${response.code}）")
        val disposition = response.header("Content-Disposition").orEmpty()
            .substringBefore(';').trim()
        // Do not mistake a successful HTTP login/error page for the attachment. A genuine HTML or
        // JSON attachment remains valid when the server explicitly labels it as an attachment.
        if (disposition.equals("attachment", ignoreCase = true)) return
        val sample = response.peekBody(4096).string()
        val json = runCatching { JsonParser.parseString(sample).asJsonObject }.getOrNull()
        val code = json?.get("code")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val loginJson = code.contains("SESSION", true) || code.contains("LOGIN", true) ||
            code.contains("AUTH", true) || code.equals("S_INVALID_SID", true)
        val loginHtml = sample.contains("<form", true) &&
            (sample.contains("password", true) || sample.contains("login", true))
        if (loginJson || loginHtml) throw MailSessionExpired()
        throw MailServiceFailure("邮箱未返回可下载的附件，请刷新后重试")
    }

    companion object {
        private fun mailboxUrl(host: String): HttpUrl {
            require(host == StudentMailSession.MAIL_HOST) { "邮箱附件地址无效" }
            return "https://${StudentMailSession.MAIL_HOST}/".toHttpUrl()
        }
    }
}
