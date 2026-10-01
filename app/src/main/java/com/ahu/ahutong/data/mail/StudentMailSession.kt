package com.ahu.ahutong.data.mail

import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.session.SessionStore
import com.google.gson.JsonParser
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Deliberately has no data-class toString: sid is a bearer credential. */
class MailSession internal constructor(
    val sid: String,
    val host: String,
    val client: OkHttpClient,
    internal val account: String,
    private val isCurrent: () -> Boolean,
    internal val cookies: MailMemoryCookieJar
) {
    internal val key: String = UUID.randomUUID().toString()
    @Volatile private var active = true
    @Volatile internal var email: String? = null
    @Volatile internal var overview: MailOverview? = null

    fun assertCurrent() {
        if (!active || !isCurrent()) {
            invalidate()
            throw MailSessionExpired()
        }
    }

    internal fun invalidate() {
        active = false
        overview = null
        email = null
        client.dispatcher.cancelAll()
        cookies.clear()
    }
}

/** Campus and mailbox cookies never share a jar; retained mailbox credentials use Keystore storage. */
object StudentMailSession {
    private const val PORTAL_HOST = "one.ahu.edu.cn"
    internal const val MAIL_HOST = "mail.stu.ahu.edu.cn"
    internal const val ENTRY_HOST = "entryhz.qiye.163.com"
    private const val PORTAL_ENTRY = "https://one.ahu.edu.cn/tp_up/view?m=up"
    private val mutex = Mutex()
    @Volatile private var cached: MailSession? = null
    @Volatile private var resetGeneration = 0L

    fun clear() {
        synchronized(this) {
            resetGeneration += 1
            cached?.invalidate()
            cached = null
            StudentMailSessionStore.clear()
        }
    }

    fun invalidate(session: MailSession) {
        synchronized(this) {
            session.invalidate()
            if (cached === session) {
                cached = null
                StudentMailSessionStore.clear()
            }
        }
    }

    fun cachedOverview(): MailOverview? = synchronized(this) {
        val session = cached ?: return@synchronized null
        try { session.assertCurrent(); session.overview } catch (_: MailSessionExpired) { null }
    }

    internal fun rememberOverview(session: MailSession, overview: MailOverview) = synchronized(this) {
        session.assertCurrent()
        session.overview = overview
    }

    internal suspend fun persist(session: MailSession) = withContext(Dispatchers.IO) {
        synchronized(this@StudentMailSession) {
            session.assertCurrent()
            if (cached === session) session.email?.let { email ->
                try {
                    StudentMailSessionStore.write(StoredMailSession(session.account, session.sid, email, session.cookies.snapshot()))
                } catch (_: Exception) {
                    // Storage failure leaves the valid in-memory session usable, without plaintext fallback.
                    StudentMailDiagnostics.record("mail.session.persistence-unavailable")
                }
            }
        }
    }

    fun mailboxCookies(session: MailSession): List<Cookie> {
        session.assertCurrent()
        return session.cookies.snapshot()
    }

    suspend fun connect(): MailSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            StudentMailDiagnostics.begin()
            cached?.let { existing ->
                try {
                    existing.assertCurrent()
                    StudentMailDiagnostics.record("mail.session.reused", "source=memory")
                    return@withLock existing
                } catch (_: IOException) {
                    invalidate(existing)
                }
            }
            val account = SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() }
                ?: throw MailServiceFailure("请先登录智慧安大")
            val reset = resetGeneration
            val identity = SessionRefreshCoordinator.currentIdentityGeneration()
            var generation = SessionRefreshCoordinator.currentGeneration()
            fun mailboxCurrent(): Boolean = reset == resetGeneration &&
                account == SessionStore.currentUser()?.xh &&
                identity == SessionRefreshCoordinator.currentIdentityGeneration() &&
                !SessionRefreshCoordinator.isExplicitlySignedOut()
            // Persisted CAS/portal cookies can be used before the UI status is hydrated at cold start.
            fun current(): Boolean = reset == resetGeneration &&
                account == SessionStore.currentUser()?.xh &&
                generation == SessionRefreshCoordinator.currentGeneration() &&
                !SessionRefreshCoordinator.isExplicitlySignedOut()
            fun checkCurrent() {
                if (!current()) throw MailServiceFailure("登录账号已变化，请重新进入邮箱")
            }
            checkCurrent()

            synchronized(this@StudentMailSession) {
                if (!mailboxCurrent()) throw MailSessionExpired()
                StudentMailSessionStore.read(account)?.let { stored ->
                    val jar = MailMemoryCookieJar().apply {
                        saveFromResponse("https://$MAIL_HOST/".toHttpUrl(), stored.cookies)
                    }
                    val restored = createSession(stored.sid, account, jar, ::mailboxCurrent).apply { email = stored.email }
                    restored.assertCurrent()
                    cached = restored
                    StudentMailDiagnostics.record("mail.session.reused", "source=encrypted-storage")
                    return@withLock restored
                }
            }

            // Only the campus host can read/write the application's first-party cookie jar.
            val portal = baseClient().cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    checkCurrent()
                    return if (url.host == PORTAL_HOST) CookieManager.cookieJar.loadForRequest(url) else emptyList()
                }
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    checkCurrent()
                    if (url.host == PORTAL_HOST) CookieManager.cookieJar.saveFromResponse(url, cookies)
                }
            }).build()
            StudentMailDiagnostics.record("portal.shared.cookies", StudentMailDiagnostics.cookies(
                CookieManager.cookieJar.loadForRequest(portalCasLoginUrl())))
            // Enter CAS explicitly to exchange the existing TGC for this portal service.
            val portalLogin = portalCasLoginUrl()
            var page = follow(portal, portalLogin, true, ::checkCurrent)
            if (page.url.encodedPath.substringBefore(';') == "/cas/login") {
                val service = page.url.queryParameter("service")?.toHttpUrlOrNull()
                if (service?.host != PORTAL_HOST || service.encodedPath != "/tp_up/view") {
                    throw MailServiceFailure("智慧安大邮箱登录入口已变化")
                }
                val password = SecureCredentialVault.wisdomPassword()?.takeIf { it.isNotBlank() }
                    ?: throw MailServiceFailure("智慧安大登录已过期，请重新登录")
                val refreshed = SessionRefreshCoordinator.refreshIfNeeded(generation,
                    scope = SessionRefreshCoordinator.Scope.CENTRAL_CAS) {
                    StudentMailDiagnostics.record("portal.cas.refresh.begin")
                    checkCurrent()
                    if (AHURepository.refreshCentralCasSession(account, password, page.url.toString())) {
                        SessionRefreshCoordinator.RefreshOutcome.SUCCESS
                    } else SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
                }
                // A concurrent sign-in can also advance the coordinator. Never adopt a different account.
                if (!refreshed || SessionRefreshCoordinator.currentGeneration() != generation + 1 ||
                    reset != resetGeneration || account != SessionStore.currentUser()?.xh ||
                    SessionRefreshCoordinator.isExplicitlySignedOut()) {
                    throw MailServiceFailure("无法续期智慧安大邮箱登录，请重新登录后重试")
                }
                generation += 1
                StudentMailDiagnostics.record("portal.cas.refresh.success")
                SessionRefreshCoordinator.commitIfCurrent(generation) {
                    checkCurrent()
                    AhuSessionState.markAuthenticated()
                }
                page = follow(portal, portalLogin, true, ::checkCurrent)
            }
            if (page.url.encodedPath.substringBefore(';') == "/cas/login") {
                throw MailServiceFailure("智慧安大登录需要验证，请重新登录")
            }
            fun ssoRequest() = Request.Builder()
                .url("https://one.ahu.edu.cn/tp_up/up/subgroup/generateSsoUrl")
                .header("Origin", "https://one.ahu.edu.cn")
                .header("Referer", PORTAL_ENTRY)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .post("{}".toRequestBody("application/json;charset=UTF-8".toMediaType()))
                .build()
            var vpnRedirect: HttpUrl? = null
            suspend fun generateSso(client: OkHttpClient, method: String, vpnBase: HttpUrl? = null): HttpUrl {
                checkCurrent()
                val request = if (vpnBase == null) ssoRequest() else ssoRequest().newBuilder()
                    .url(vpnBase.resolve("up/subgroup/generateSsoUrl")!!)
                    .header("Origin", "https://wvpn.ahu.edu.cn")
                    .header("Referer", vpnBase.resolve("view?m=up").toString()).build()
                StudentMailDiagnostics.record("$method.sso.request", "cookies=" + StudentMailDiagnostics.cookies(client.cookieJar.loadForRequest(request.url)))
                return client.newCall(request).awaitResponse().use { response ->
                    currentCoroutineContext().ensureActive()
                    checkCurrent()
                    val redirect = response.header("Location")?.let(request.url::resolve)
                    if (redirect?.scheme == "https" && redirect.host == "wvpn.ahu.edu.cn" && redirect.port == 443 &&
                        redirect.encodedPath.matches(Regex("/https/[a-f0-9]+/tp_up/view"))) vpnRedirect = redirect
                    StudentMailDiagnostics.record("$method.sso.response", "status=${response.code} redirect=" + (redirect?.let(StudentMailDiagnostics::route) ?: "none"))
                    if (!response.isSuccessful) throw MailServiceFailure("无法获取学生邮箱入口（HTTP ${response.code}）")
                    val url = runCatching {
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject.get("ssourl")?.asString
                    }.getOrNull() ?: throw MailServiceFailure("学生邮箱未开通或智慧安大登录已过期")
                    secureUrl(url, false).also {
                        if (it.host != ENTRY_HOST || it.encodedPath != "/domain/oa/Entry") {
                            throw MailServiceFailure("学生邮箱登录入口已变化")
                        }
                        StudentMailDiagnostics.record("$method.sso.success", StudentMailDiagnostics.route(it))
                    }
                }
            }
            val mailboxEntry = try {
                generateSso(portal, "portal-direct")
            } catch (error: MailServiceFailure) {
                // Off-campus portal APIs redirect to the school's authenticated WebVPN.
                // The VPN maintains its own upstream cookies. Redeem a NEW, unused portal
                // ticket through that proxy instead of copying a direct portal SESSION.
                val vpnView = vpnRedirect ?: throw error
                val seed = CookieManager.cookieJar.loadForRequest(portalLogin)
                    .filter { it.name in setOf("CASTGC", "TGC") }
                val vpn = baseClient().cookieJar(PortalMemoryCookieJar(seed, ::checkCurrent)).build()
                StudentMailDiagnostics.record("portal.webvpn.begin")
                vpn.newCall(Request.Builder().url("https://wvpn.ahu.edu.cn/login").build()).awaitResponse().use {
                    checkCurrent()
                    if (it.code !in setOf(200, 301, 302, 303)) throw MailServiceFailure("无法连接学校 WebVPN")
                }
                val vpnLogin = "https://one.ahu.edu.cn/cas/login".toHttpUrl().newBuilder()
                    .addQueryParameter("service", "https://wvpn.ahu.edu.cn/login?cas_login=true").build()
                val loginPage = follow(vpn, vpnLogin, true, ::checkCurrent)
                if (loginPage.url.host != "wvpn.ahu.edu.cn") throw MailServiceFailure("学校 WebVPN 登录未完成，请重新登录智慧安大")
                val callback = vpn.newCall(Request.Builder().url(portalLogin).build()).awaitResponse().use {
                    checkCurrent()
                    StudentMailDiagnostics.record("portal.webvpn.ticket", "status=${it.code}")
                    if (it.code != 302) throw MailServiceFailure("学校未签发门户认证票据，请重新登录智慧安大")
                    it.header("Location")?.let(portalLogin::resolve)?.let { target -> secureUrl(target.toString(), true) }
                }
                val proxyCallback = proxyPortalCallback(vpnView, callback)
                val portalPage = follow(vpn, proxyCallback, true, ::checkCurrent)
                if (portalPage.url.host != vpnView.host || portalPage.url.encodedPath != vpnView.encodedPath) {
                    throw MailServiceFailure("学校 WebVPN 门户认证未完成")
                }
                generateSso(vpn, "portal-webvpn", vpnView.resolve("./")!!)
            }
            val jar = MailMemoryCookieJar()
            val transport = baseClient().cookieJar(jar).build()
            try {
                StudentMailDiagnostics.record("mail.entry.begin")
                var mailPage = follow(transport, mailboxEntry, false, ::checkCurrent)
                var sid = mailPage.url.queryParameter("sid")
                if (mailPage.url.host == ENTRY_HOST) {
                    val redirect = StudentMailProtocol.extractRedirect(mailPage.body, mailPage.url.toString())
                        ?: throw MailServiceFailure("学生邮箱登录页面已变化，请稍后重试")
                    val target = secureUrl(redirect, false)
                    if (target.host != MAIL_HOST || target.encodedPath != "/redirect") {
                        throw MailServiceFailure("学生邮箱跳转地址不受信任")
                    }
                    sid = target.queryParameter("sid")
                    StudentMailDiagnostics.record("mail.entry.html-redirect", StudentMailDiagnostics.route(target))
                    mailPage = follow(transport, target, false, ::checkCurrent)
                }
                sid = mailPage.url.queryParameter("sid") ?: sid
                if (mailPage.url.host != MAIL_HOST || sid.isNullOrBlank() ||
                    jar.snapshot().isEmpty()) throw MailServiceFailure("学生邮箱登录未完成，请重新连接")
                checkCurrent()
                currentCoroutineContext().ensureActive()
                val session = createSession(sid, account, jar, ::mailboxCurrent)
                StudentMailDiagnostics.record("mail.session.created", "sid-present=true cookie-count=${jar.snapshot().size}")
                synchronized(this@StudentMailSession) {
                    session.assertCurrent()
                    cached = session
                }
                session
            } catch (error: Throwable) {
                jar.clear()
                throw error
            }
        }
    }

    private fun createSession(sid: String, account: String, jar: MailMemoryCookieJar, current: () -> Boolean): MailSession {
        lateinit var session: MailSession
        val client = baseClient().cookieJar(jar).addInterceptor { chain ->
            session.assertCurrent()
            val url = chain.request().url
            if (url.scheme != "https" || url.host != MAIL_HOST || url.port != 443) {
                throw MailServiceFailure("邮箱请求地址不受信任")
            }
            val response = chain.proceed(chain.request())
            try { session.assertCurrent(); response } catch (error: IOException) {
                response.close()
                throw error
            }
        }.build()
        session = MailSession(sid, MAIL_HOST, client, account, current, jar)
        return session
    }

    private fun baseClient() = AhuHttp.plain(
        readTimeoutSeconds = 30,
        callTimeoutSeconds = 45,
        retryOnConnectionFailure = false,
        followRedirects = false,
        followSslRedirects = false
    ).addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36 Edg/154.0.0.0")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build())
    }

    internal fun portalCasLoginUrl(): HttpUrl = "https://one.ahu.edu.cn/cas/login".toHttpUrl().newBuilder()
        .addQueryParameter("service", PORTAL_ENTRY)
        .build()

    internal fun proxyPortalCallback(vpnView: HttpUrl, callback: HttpUrl?): HttpUrl {
        val trustedProxyPath = "/https/77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b/tp_up/view"
        if (vpnView.scheme != "https" || vpnView.host != "wvpn.ahu.edu.cn" || vpnView.port != 443 ||
            vpnView.username.isNotEmpty() || vpnView.password.isNotEmpty() || vpnView.encodedPath != trustedProxyPath ||
            callback?.scheme != "https" || callback.host != PORTAL_HOST || callback.port != 443 ||
            callback.username.isNotEmpty() || callback.password.isNotEmpty() || callback.encodedPath != "/tp_up/view" ||
            callback.queryParameter("ticket").isNullOrBlank() || callback.queryParameter("m") != "up") {
            throw MailServiceFailure("门户认证回调地址不受信任")
        }
        return vpnView.newBuilder().encodedQuery(callback.encodedQuery).build()
    }

    private data class Page(val url: HttpUrl, val body: String)

    private suspend fun follow(
        client: OkHttpClient,
        initial: HttpUrl,
        campus: Boolean,
        checkCurrent: () -> Unit
    ): Page {
        var url = secureUrl(initial.toString(), campus)
        repeat(12) {
            currentCoroutineContext().ensureActive()
            checkCurrent()
            StudentMailDiagnostics.record("http.get", StudentMailDiagnostics.route(url))
            client.newCall(Request.Builder().url(url).build()).awaitResponse().use { response ->
                currentCoroutineContext().ensureActive()
                checkCurrent()
                StudentMailDiagnostics.record("http.response", "status=${response.code} cookies=" + StudentMailDiagnostics.cookies(client.cookieJar.loadForRequest(url)))
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    val target = response.header("Location")?.let(url::resolve)
                        ?: throw MailServiceFailure("邮箱登录跳转缺少地址")
                    url = secureUrl(target.toString(), campus)
                    StudentMailDiagnostics.record("http.redirect", StudentMailDiagnostics.route(url))
                } else {
                    if (!response.isSuccessful) throw MailServiceFailure("邮箱登录失败（HTTP ${response.code}）")
                    val body = response.body?.string().orEmpty()
                    return Page(url, body)
                }
            }
        }
        throw MailServiceFailure("邮箱登录跳转次数过多")
    }

    internal fun secureUrl(address: String, campus: Boolean): HttpUrl {
        var url = address.toHttpUrlOrNull() ?: throw MailServiceFailure("邮箱登录地址无效")
        if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.host !in if (campus) setOf(PORTAL_HOST, "wvpn.ahu.edu.cn") else setOf(ENTRY_HOST, MAIL_HOST)) {
            throw MailServiceFailure("邮箱登录地址不受信任")
        }
        if (!campus && url.host == MAIL_HOST && url.scheme == "http" && url.port == 80) {
            url = url.newBuilder().scheme("https").port(443).build()
        }
        if (url.scheme != "https" || url.port != 443) throw MailServiceFailure("邮箱登录需要安全连接")
        return url
    }
}

/** Each response replaces earlier cookies by RFC identity, independent of hostOnly/secure differences. */
internal class PortalMemoryCookieJar(seed: List<Cookie>, private val checkCurrent: () -> Unit) : CookieJar {
    // Parent-domain cookies can match both hosts. Keep their response origin as an
    // additional boundary so the direct campus session never enters the VPN jar.
    private val storedByHost = mutableMapOf(
        "one.ahu.edu.cn" to seed.groupBy { Triple(it.name, it.domain, it.path) }
            .values.map { same -> same.firstOrNull { it.hostOnly } ?: same.last() }.toMutableList()
    )
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        checkCurrent()
        if (url.host !in setOf("one.ahu.edu.cn", "wvpn.ahu.edu.cn")) return emptyList()
        val stored = storedByHost[url.host] ?: return emptyList()
        stored.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return stored.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        checkCurrent()
        if (url.host !in setOf("one.ahu.edu.cn", "wvpn.ahu.edu.cn")) return
        val stored = storedByHost.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { cookie ->
            if (cookie.domain != url.host && (cookie.hostOnly || !url.host.endsWith(".${cookie.domain}"))) return@forEach
            stored.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) stored += cookie
        }
    }
}

internal fun canResumePersistedCampusSession(
    signedOut: Boolean, status: AhuSessionState.Status, hasAccount: Boolean
): Boolean = !signedOut && status == AhuSessionState.Status.Anonymous && hasAccount

internal class MailMemoryCookieJar : CookieJar {
    private val cookiesByHost = mutableMapOf<String, MutableList<Cookie>>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host !in setOf(StudentMailSession.MAIL_HOST, StudentMailSession.ENTRY_HOST)) return
        val stored = cookiesByHost.getOrPut(url.host) { mutableListOf() }
        cookies.filter { cookie ->
            cookie.domain == url.host || (!cookie.hostOnly && url.host.endsWith(".${cookie.domain}"))
        }.forEach { cookie ->
            stored.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) stored += cookie
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = cookiesByHost[url.host] ?: return emptyList()
        stored.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return stored.filter { it.matches(url) }
    }
    @Synchronized fun snapshot(): List<Cookie> = cookiesByHost[StudentMailSession.MAIL_HOST]
        .orEmpty().filter { it.expiresAt > System.currentTimeMillis() }
    @Synchronized fun clear() { cookiesByHost.clear() }
}
