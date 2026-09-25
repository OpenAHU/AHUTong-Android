package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.utils.DES
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

internal data class PortalPage(val url: String, val status: Int, val html: String)

/** HTTP operations are injected so CAS and GMIS page handling can be tested without accounts. */
internal class AcademicPortalLogin(
    private val diagnostic: (String) -> Unit = {},
    private val request: suspend (String, Map<String, String>?) -> PortalPage
) {
    suspend fun login(entry: String, username: String, password: String, user: User): PortalLoginResult {
        var page = request(entry, null)
        val graduate = entry == GMIS_ENTRY
        if (isSuccess(page, graduate, user)) return PortalLoginResult(PortalLoginStatus.SUCCESS, sessionPageUrl = page.url)
        if (page.status == 412) return PortalLoginResult(PortalLoginStatus.VERIFICATION_REQUIRED)
        if (page.status !in 200..299) return unavailable(page.status)

        val url = page.url.toHttpUrlOrNull()
        val doc = Jsoup.parse(page.html)
        val form = doc.selectFirst("form#loginForm")
        if (url?.host != "one.ahu.edu.cn" || url.encodedPath.substringBefore(';') != "/cas/login" || form == null) {
            return rejected(graduate)
        }
        val lt = form.selectFirst("input[name=lt]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: return PortalLoginResult(PortalLoginStatus.UNAVAILABLE, "统一认证页面结构发生变化")
        val action = url.resolve(form.attr("action"))
        if (action?.scheme != "https" || action.host != "one.ahu.edu.cn" ||
            action.encodedPath.substringBefore(';') != "/cas/login"
        ) return PortalLoginResult(PortalLoginStatus.UNAVAILABLE, "统一认证提交地址无效")

        val cipher = DES().strEnc(username + password + lt, "1", "2", "3")
        val fields = mapOf("rsa" to cipher, "ul" to username.length.toString(), "pl" to password.length.toString())
        val device = request("https://one.ahu.edu.cn/cas/device", fields + ("method" to "login"))
        if (device.status == 412) return PortalLoginResult(PortalLoginStatus.VERIFICATION_REQUIRED)
        if (device.status !in 200..299) return unavailable(device.status)
        page = request(
            action.toString(),
            fields + mapOf(
                "lt" to lt,
                "execution" to (form.selectFirst("input[name=execution]")?.attr("value") ?: "e1s1"),
                "_eventId" to "submit"
            )
        )
        return when {
            page.status == 412 -> PortalLoginResult(PortalLoginStatus.VERIFICATION_REQUIRED)
            page.status !in 200..299 -> unavailable(page.status)
            isSuccess(page, graduate, user) -> PortalLoginResult(PortalLoginStatus.SUCCESS, sessionPageUrl = page.url)
            else -> rejected(graduate)
        }
    }

    private fun rejected(graduate: Boolean) = PortalLoginResult(
        PortalLoginStatus.REJECTED,
        if (graduate) "未确认研究生学生会话，请确认该账号能进入研究生教务学生首页"
        else "本科教务未接受此账号"
    )

    private fun unavailable(status: Int) =
        PortalLoginResult(PortalLoginStatus.UNAVAILABLE, "教务服务返回 HTTP $status")

    private suspend fun isSuccess(page: PortalPage, graduate: Boolean, user: User): Boolean {
        val url = page.url.toHttpUrlOrNull() ?: return false
        val studentHome = if (graduate) GmisSessionVerifier.studentHomeUrl(page)?.let {
            request(it, null)
        } else null
        val succeeded = if (graduate) GmisSessionVerifier.isStudentSession(page, user.xh, user.name, studentHome)
        else page.status in 200..299 && url.scheme == "https" &&
            url.host == "jw.ahu.edu.cn" && url.encodedPath.trimEnd('/') == "/student/home"
        val stage = when {
            url.host == "one.ahu.edu.cn" -> "CAS"
            url.encodedPath.contains("error", ignoreCase = true) -> "error-page"
            url.encodedPath.contains("login", ignoreCase = true) -> "login-page"
            else -> "portal-page"
        }
        diagnostic("portal=${if (graduate) "GMIS" else "JWXT"} http=${page.status} stage=$stage verified=$succeeded")
        if (graduate && !succeeded) diagnostic(GmisSessionVerifier.diagnostics(page, user.xh, user.name))
        return succeeded
    }

    companion object {
        const val UNDERGRADUATE_ENTRY = "https://jw.ahu.edu.cn/student/sso/login"
        const val GMIS_ENTRY = "https://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx"
    }
}

internal object GmisSessionVerifier {
    private fun normalizedPath(url: String): String =
        url.toHttpUrlOrNull()?.encodedPath.orEmpty().lowercase()
            .replace(Regex("/\\(s\\([^)]*\\)\\)"), "").trimEnd('/')

    private fun isGmisPage(page: PortalPage): Boolean {
        val url = page.url.toHttpUrlOrNull() ?: return false
        return page.status in 200..299 && url.scheme == "https" && url.host == "gmis.ahu.edu.cn"
    }

    fun studentHomeUrl(page: PortalPage): String? {
        if (!isGmisPage(page) || normalizedPath(page.url) != "/gmis5/student/default/index") return null
        val doc = Jsoup.parse(page.html)
        if (doc.title().trim() != "学生端" || doc.select("input[type=password]").isNotEmpty()) return null
        val base = page.url.toHttpUrlOrNull() ?: return null
        return doc.select("iframe[src], frame[src]").mapNotNull { base.resolve(it.attr("src")) }
            .firstOrNull {
                it.scheme == "https" && it.host == base.host &&
                    normalizedPath(it.toString()) == "/gmis5/student/default/home" &&
                    it.encodedPath.substringBefore("/student/", "") ==
                    base.encodedPath.substringBefore("/student/", "")
            }?.toString()
    }

    fun diagnostics(page: PortalPage, studentId: String?, displayName: String?): String {
        val doc = Jsoup.parse(page.html)
        val text = doc.text()
        fun path(address: String): String =
            address.toHttpUrlOrNull()?.encodedPath.orEmpty()
                .replace(Regex("(?i)/\\(s\\([^)]*\\)\\)"), "/[session]")
                .replace(Regex("[0-9]{5,}"), "[id]").take(100)
        val base = page.url.toHttpUrlOrNull()
        val frames = doc.select("iframe[src], frame[src]").mapNotNull {
            base?.resolve(it.attr("src"))?.toString()?.let(::path)
        }.take(5)
        val title = doc.title().replace(studentId.orEmpty().ifBlank { "[no-id]" }, "[id]")
            .replace(displayName.orEmpty().ifBlank { "[no-name]" }, "[name]").take(80)
        return "GMIS evidence path=${path(page.url)} title=$title " +
            "idPresent=${!studentId.isNullOrBlank() && text.contains(studentId)} " +
            "namePresent=${!displayName.isNullOrBlank() && text.contains(displayName)} " +
            "logoutText=${text.contains("退出") || text.contains("注销")} " +
            "studentText=${text.contains("学生") || text.contains("培养") || text.contains("选课")} " +
            "passwordInputs=${doc.select("input[type=password]").size} frames=$frames"
    }

    fun isStudentSession(
        page: PortalPage,
        studentId: String?,
        displayName: String?,
        studentHome: PortalPage? = null
    ): Boolean {
        // Observed live GMIS shell. Both index and its home frame redirect anonymous
        // requests to /home/stulogin, so verify the protected frame as well as the shell.
        if (studentHomeUrl(page) != null) {
            if (studentHome == null || !isGmisPage(studentHome) ||
                normalizedPath(studentHome.url) != "/gmis5/student/default/home" ||
                studentHome.html.isBlank()
            ) return false
            val home = Jsoup.parse(studentHome.html)
            return home.select("input[type=password], form#loginForm, #errorInfo").isEmpty() &&
                !home.title().contains("错误")
        }
        val url = page.url.toHttpUrlOrNull() ?: return false
        if (page.status !in 200..299 || url.scheme != "https" || url.host != "gmis.ahu.edu.cn") return false
        val path = url.encodedPath.lowercase().replace(Regex("/\\(s\\([^)]*\\)\\)"), "")
        if (!path.startsWith("/gmis5/student/") || listOf("login", "error", "forgotpwd").any { it in path }) return false
        val doc = Jsoup.parse(page.html)
        if (doc.select("input[type=password], form#loginForm, #errorInfo").isNotEmpty()) return false
        val text = doc.text()
        if (listOf("教师端", "导师端", "请选择角色", "用户名或密码").any { it in text }) return false
        val logout = doc.select("a, button, input[type=button]").any {
            listOf("退出", "注销").any { label -> label in it.text() || label in it.attr("value") } ||
                Regex("(?i)logout|logoff|signout").containsMatchIn(it.attr("href") + it.attr("onclick"))
        }
        val identity = listOfNotNull(studentId, displayName).filter { it.isNotBlank() }.any {
            Regex("(?<![A-Za-z0-9])" + Regex.escape(it) + "(?![A-Za-z0-9])").containsMatchIn(text)
        }
        val studentNavigation = listOf("学籍信息", "学生个人", "个人培养", "我的课表", "课程学习", "选课", "培养计划").any { it in text }
        return logout && identity && studentNavigation
    }
}
