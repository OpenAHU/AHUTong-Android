package com.ahu.ahutong.data.xuexiaotong

import android.content.Context
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ChaoxingApi(private val context: Context) {

    private val UA = "Mozilla/5.0 (Linux; Android 10; HD1910) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"

    private val cookieJar = PersistentCookieJar(context)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    fun hasSession(): Boolean = Store.hasCookie()

    fun clearSession() {
        client.dispatcher.cancelAll()
        Store.clearCookie()
        cookieJar.clear()
    }

    suspend fun loginByPassword(phone: String, pwd: String): String = withContext(Dispatchers.IO) {
        val uname = Aes.encrypt(phone.trim())
        val password = Aes.encrypt(pwd)

        val form = FormBody.Builder()
            .add("fid", "-1")
            .add("uname", uname)
            .add("password", password)
            .add("refer", "http%3A%2F%2Fi.mooc.chaoxing.com")
            .add("t", "true")
            .add("forbidotherlogin", "0")
            .add("validate", "")
            .add("doubleFactorLogin", "0")
            .add("independentId", "0")
            .add("independentNameId", "0")
            .build()

        val req = Request.Builder()
            .url("https://passport2.chaoxing.com/fanyalogin")
            .post(form)
            .header("User-Agent", UA)
            .header("Referer", "https://passport2.chaoxing.com/login")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: throw IOException("登录接口返回空")
            val data = try { JSONObject(body) } catch (e: Exception) { throw IOException("登录接口返回异常") }
            if (!data.optBoolean("status", false)) {
                val msg = data.optString("msg2", data.optString("msg", "用户名或密码错误"))
                throw IOException(msg)
            }
            val jumpUrl = data.optString("url", "")
            try { if (jumpUrl.isNotEmpty()) getText(jumpUrl) } catch (e: Exception) { }
            val domains = listOf(
     "https://mooc2-ans.chaoxing.com/visit/interaction",
     "https://mooc1.chaoxing.com/visit/interaction",
     "https://mobilelearn.chaoxing.com/page/active/stuActiveList?courseid=1&clazzid=1&cpi=1&ut=s&t=${System.currentTimeMillis()}&stuenc=1&fid=1",
     "https://i.mooc.chaoxing.com/space/index",
     "https://passport2-api.chaoxing.com/",
     "https://stat2-ans.chaoxing.com/"
    )
    for (d in domains) { try { getText(d) } catch (e: Exception) { } }

            val cookie = cookieJar.cookieString()
            Store.saveCookie(cookie)
            cookie
        }
    }

    suspend fun silentRelogin(): Boolean = withContext(Dispatchers.IO) {
        val cred = Store.getCredential() ?: return@withContext false
        val backup = Store.getCookie()
        clearSession()
        try {
            loginByPassword(cred.first, cred.second)
            true
        } catch (e: Exception) {
            if (backup.isNotEmpty()) {
                cookieJar.restoreFromString(backup)
                Store.saveCookie(backup)
            }
            false
        }
    }

    suspend fun checkLogin(): Boolean = withContext(Dispatchers.IO) {
        try {
            val html = postText(
                "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata",
                "courseType=1&courseFolderId=0&query=&superstarClass=0&courseFolderSize=0",
                referer = "https://mooc2-ans.chaoxing.com/visit/interaction"
            )
            html.contains("course-list") || html.contains("learnCourse") || html.contains("course clearfix")
        } catch (e: Exception) { false }
    }

    private fun baseHeaders(referer: String?): Map<String, String> {
        val h = mutableMapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "zh-CN,zh;q=0.9",
            "User-Agent" to UA
        )
        if (referer != null) h["Referer"] = referer
        return h
    }

    private suspend fun get(url: String, referer: String? = null): Response = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().apply {
            baseHeaders(referer).forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute()
    }

    private suspend fun getText(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val res = get(url, referer)
        res.use { r ->
            if (r.code in 200..299) r.body?.string() ?: "" else throw IOException("HTTP ${r.code} $url")
        }
    }

    private suspend fun postText(url: String, body: String, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .apply {
                    baseHeaders(referer).forEach { (k, v) -> header(k, v) }
                }.build()
            client.newCall(req).execute().use { r ->
                if (r.code in 200..299) r.body?.string() ?: "" else throw IOException("HTTP ${r.code} $url")
            }
        }

    private suspend fun postForm(url: String, form: FormBody, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .post(form)
                .apply {
                    baseHeaders(referer).forEach { (k, v) -> header(k, v) }
                }.build()
            client.newCall(req).execute().use { r ->
                if (r.code in 200..299) r.body?.string() ?: "" else throw IOException("HTTP ${r.code} $url")
            }
        }

    suspend fun fetchCourses(): List<Course> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("courseType", "1")
            .add("courseFolderId", "0")
            .add("query", "")
            .add("superstarClass", "0")
            .add("courseFolderSize", "0")
            .build()
        val html = postForm(
            "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata",
            form,
            "https://mooc2-ans.chaoxing.com/visit/interaction"
        )

        val courses = mutableListOf<Course>()
        val blocks = html.split("<div class=\"course clearfix")
        for (i in 1 until blocks.size) {
            val block = blocks[i]
            val info = Regex("""info="(\d+)_(\d+)"""").find(block) ?: continue
            val clazzId = info.groupValues[1]
            val cpi = info.groupValues[2]

            var name = Regex("""class="course-name[^"]*"[^>]*title="([^"]*)"""").find(block)
                ?.groupValues?.get(1)
                ?: Regex("""title="([^"]*)"[^>]*class="course-name""").find(block)?.groupValues?.get(1)
            if (name.isNullOrEmpty()) continue
            name = name.trim()

            val href = Regex("""href="(https?://mooc1\.chaoxing\.com/visit/stucoursemiddle[^"]*)"""").find(block)
                ?.groupValues?.get(1) ?: ""
            val courseId = Regex("""class="courseId"[^>]*value="(\d+)"""").find(block)
                ?.groupValues?.get(1) ?: ""

            courses.add(Course(courseId, clazzId, cpi, name, href))
        }

        if (courses.isNotEmpty()) Store.saveCourses(courses)
        courses
    }

    suspend fun fetchCourseKeys(course: Course): CourseKeys = withContext(Dispatchers.IO) {
        val url = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=${course.courseId}" +
            "&clazzid=${course.clazzId}&cpi=${course.cpi}&ismooc2=1&v=2"

        var html: String
        var enc: String
        var workEnc: String
        try {
            val res = get(url, "https://mooc2-ans.chaoxing.com/visit/interaction")
            val finalUrl = res.request.url.toString()
            html = res.use { r ->
                if (r.code in 200..299) r.body?.string() ?: "" else throw IOException("HTTP ${r.code} $url")
            }
            enc = Regex("""[?&]enc=([a-f0-9]{32})""", RegexOption.IGNORE_CASE).find(finalUrl)?.groupValues?.get(1) ?: ""
            workEnc = extractHiddenValue(html, "workEnc")
        } catch (e: Exception) {
            html = ""; enc = ""; workEnc = ""
        }

        if (enc.isEmpty() && html.isNotEmpty()) {
            enc = Regex("""enc=([a-f0-9]{32})""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: ""
        }

        if (enc.isEmpty() || workEnc.isEmpty()) {
            try {
                getText("https://mooc2-ans.chaoxing.com/visit/interaction")
                val res2 = get(url, "https://mooc2-ans.chaoxing.com/visit/interaction")
                val finalUrl2 = res2.request.url.toString()
                html = res2.use { r -> r.body?.string() ?: "" }
                enc = Regex("""[?&]enc=([a-f0-9]{32})""", RegexOption.IGNORE_CASE).find(finalUrl2)?.groupValues?.get(1) ?: ""
                if (enc.isEmpty()) {
                    enc = Regex("""enc=([a-f0-9]{32})""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: ""
                }
                workEnc = extractHiddenValue(html, "workEnc")
            } catch (e: Exception) { }
        }

        if (workEnc.isEmpty() && enc.isNotEmpty()) {
            try {
                val listUrl = "https://mooc1.chaoxing.com/mooc2/work/list?courseId=${course.courseId}" +
                    "&classId=${course.clazzId}&cpi=${course.cpi}&ut=s&t=${System.currentTimeMillis()}" +
                    "&stuenc=$enc&enc=&status=0&pageNum=1"
                val listHtml = getText(listUrl, "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu")
                workEnc = Regex("""enc=([a-f0-9]{32})""", RegexOption.IGNORE_CASE).find(listHtml)?.groupValues?.get(1) ?: ""
            } catch (e: Exception) { }
        }

        if (enc.isEmpty() || workEnc.isEmpty()) {
            throw IOException("课程 ${course.name} 密钥解析失败")
        }
        CourseKeys(enc, workEnc)
    }

    private fun extractHiddenValue(html: String, id: String): String {
        val re = Regex("""<input[^>]*id=["']?$id["']?[^>]*value=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val m = re.find(html)
        if (m != null) return m.groupValues[1]
        val re2 = Regex("""<input[^>]*value=["']([^"']*)["'][^>]*id=["']?$id["']?""", RegexOption.IGNORE_CASE)
        return re2.find(html)?.groupValues?.get(1) ?: ""
    }

    suspend fun fetchCourseWorks(course: Course, keys: CourseKeys): List<Work> =
        withContext(Dispatchers.IO) {
            val works = mutableListOf<Work>()
            val baseParams = "courseId=${course.courseId}&classId=${course.clazzId}&cpi=${course.cpi}" +
                "&ut=s&t=${System.currentTimeMillis()}&stuenc=${keys.enc}&enc=${keys.workEnc}"

            var pageNum = 1
            var totalPages = 1
            var hasMore = true

            while (hasMore && pageNum <= 50) {
                val url = "https://mooc1.chaoxing.com/mooc2/work/list?$baseParams&status=0&pageNum=$pageNum"
                val referer = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?courseid=${course.courseId}" +
                    "&clazzid=${course.clazzId}&cpi=${course.cpi}"
                val html = getText(url, referer)

                if (pageNum == 1) {
                    val totalMatch = Regex("""共(\d+)页""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""totalPage['":\s]+(\d+)""", RegexOption.IGNORE_CASE).find(html)
                    if (totalMatch != null) {
                        totalPages = totalMatch.groupValues[1].toIntOrNull() ?: 1
                    } else if (!html.contains("work/task")) {
                        hasMore = false; break
                    }
                }

                val liRe = Regex(
                    """<li[^>]*data="(https?://mooc1\.chaoxing\.com/mooc-ans/mooc2/work/task[^"]*)"[^>]*>([\s\S]*?)</li>"""
                )
                var pageWorks = 0
                for (m in liRe.findAll(html)) {
                    var detailUrl = m.groupValues[1].replace("&amp;", "&")
                    val liContent = m.groupValues[2]

                    var title = Regex("""<p[^>]*class="[^"]*overHidden2[^"]*"[^>]*>([^<]*)</p>""").find(liContent)
                        ?.groupValues?.get(1)?.trim()
                    if (title.isNullOrEmpty()) {
                        title = Regex("""aria-label="([^"]*?)"""").find(m.value)
                            ?.groupValues?.get(1)?.split(";")?.firstOrNull()?.trim()
                    }
                    if (title.isNullOrEmpty()) title = "未命名作业"

                    val statusRaw = Regex("""<p[^>]*class="[^"]*status[^"]*"[^>]*>([\s\S]*?)</p>""").find(liContent)
                        ?.groupValues?.get(1)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: ""

                    val workId = Regex("""workId=(\d+)""").find(detailUrl)?.groupValues?.get(1) ?: ""
                    val answerId = Regex("""answerId=(\d+)""").find(detailUrl)?.groupValues?.get(1) ?: ""

                    val color = Store.getCourseColorCached(course.courseId)
                        ?: CourseColors.byCourseId(course.courseId)

                    works.add(
                        Work(
                            workId = workId, answerId = answerId, courseId = course.courseId,
                            courseName = course.name, title = title, status = statusRaw,
                            detailUrl = detailUrl, colorBg = color.bg, colorText = color.text
                        )
                    )
                    pageWorks++
                }

                if (pageWorks == 0) { hasMore = false; break }
                if (pageNum >= totalPages) { hasMore = false } else { pageNum++; delay(800) }
            }
            works
        }

    suspend fun fetchWorkDeadline(work: Work): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val url = work.detailUrl
        val referer = "https://mooc1.chaoxing.com/mooc2/work/list?courseId=${work.courseId}"
        val html = getText(url, referer)
        val referenceMillis = work.startTs?.takeIf { start ->
            start > 0L && work.endTs?.let { it >= start } == true
        } ?: System.currentTimeMillis()

        val timeRe = Regex("""作答时间:\s*<em>([\d-]+\s[\d:]+)</em>\s*至\s*<em>([\d-]+\s[\d:]+)</em>""")
        val m1 = timeRe.find(html)
        if (m1 != null) {
            WorkDeadlineParser.parseRange(m1.groupValues[1], m1.groupValues[2], referenceMillis)
                ?.let { return@withContext it }
        }
        val plainRe = Regex("""作答时间:\s*([\d-]+\s[\d:]+)\s*至\s*([\d-]+\s[\d:]+)""")
        val m2 = plainRe.find(html)
        if (m2 != null) {
            WorkDeadlineParser.parseRange(m2.groupValues[1], m2.groupValues[2], referenceMillis)
                ?.let { return@withContext it }
        }
        null
    }

    interface ProgressListener {
        fun onProgress(done: Int, total: Int, message: String)
    }

    suspend fun syncAllWorks(listener: ProgressListener? = null): List<Work> =
        withContext(Dispatchers.IO) {
            listener?.onProgress(0, 0, "正在获取课程列表...")
            // 每次同步都刷新课程列表，新加入的课程无需退出登录即可看到
            var courses = try { fetchCourses() } catch (e: Exception) { Store.getCourses() }
            if (courses.isEmpty()) throw IOException("课程列表获取为空，请稍后重试")

            val existing = Store.getWorks()
            val existingByCourse = existing.groupBy { it.courseId }.mapValues { (_, v) ->
                v.associateBy { it.workId }
            }

            val allWorks = mutableListOf<Work>()
            var done = 0
            val total = courses.size

            for (course in courses) {
                listener?.onProgress(done, total, "正在处理：${course.name}")
                try {
                    val keys = fetchCourseKeys(course)
                    delay(600)
                    val works = fetchCourseWorks(course, keys)
                    delay(600)

                    for (work in works) {
                        val previous = existingByCourse[course.courseId]?.get(work.workId)
                        var startTs = previous?.startTs
                        var endTs = previous?.endTs

                        // 每次同步都重新抓取截止时间，确保延期后的时间更新
                        try {
                            val dl = fetchWorkDeadline(work.copy(startTs = startTs, endTs = endTs))
                            if (dl != null) {
                                startTs = dl.first
                                endTs = dl.second
                            }
                            delay(800)
                        } catch (_: Exception) { }

                        allWorks.add(work.copy(startTs = startTs, endTs = endTs,
                            rawStart = previous?.rawStart ?: "", rawEnd = previous?.rawEnd ?: ""))
                    }
                } catch (e: Exception) {
                    // 单门课程失败时保留旧数据，避免该课程作业从日历消失
                    val oldCourseWorks = existingByCourse[course.courseId]?.values ?: emptyList()
                    allWorks.addAll(oldCourseWorks)
                }
                done++
                listener?.onProgress(done, total, "已完成 $done/$total 门课程")
            }

            if (allWorks.isEmpty() && Store.getWorks().isNotEmpty()) {
                throw IOException("同步结果为空，已保留本地数据，请稍后重试")
            }

            Store.saveWorks(allWorks)
            Store.saveLastSync(System.currentTimeMillis())
            listener?.onProgress(total, total, "同步完成")
            allWorks
        }

    suspend fun syncCourseProgress(listener: ProgressListener? = null): List<CourseProgress> =
        withContext(Dispatchers.IO) {
            listener?.onProgress(0, 0, "正在获取课程进度...")
            // 每次同步都刷新课程列表
            var courses = try { fetchCourses() } catch (e: Exception) { Store.getCourses() }
            if (courses.isEmpty()) throw IOException("课程列表获取为空，请稍后重试")

            val existingProgress = Store.getCourseProgress().associateBy { it.courseId }
            val result = mutableListOf<CourseProgress>()
            var done = 0
            val total = courses.size

            for (course in courses) {
                listener?.onProgress(done, total, "正在处理：${course.name}")
                try {
                    val url = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse" +
                        "?courseid=${course.courseId}&clazzid=${course.clazzId}&cpi=${course.cpi}&ut=s"
                    val html = getText(url, "https://mooc2-ans.chaoxing.com/visit/interaction")
                    val m = Regex("""已完成任务点[\s\S]*?<span[^>]*>(\d+)</span>\s*/\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(html)
                    if (m != null) {
                        val finish = m.groupValues[1].toIntOrNull() ?: 0
                        val jobcount = m.groupValues[2].toIntOrNull() ?: 0
                        val percent = if (jobcount > 0) (finish * 100 / jobcount) else 0
                        result.add(CourseProgress(course.courseId, course.clazzId, course.cpi,
                            course.name, finish, jobcount, percent, System.currentTimeMillis()))
                    }
                    delay(800)
                } catch (e: Exception) {
                    // 单门课程失败时保留旧数据
                    val old = existingProgress[course.courseId]
                    if (old != null) result.add(old)
                }
                done++
                listener?.onProgress(done, total, "已完成 $done/$total 门课程")
            }

            if (result.isEmpty() && Store.getCourseProgress().isNotEmpty()) {
                throw IOException("课程进度获取为空，已保留本地数据，请稍后重试")
            }
            Store.saveCourseProgress(result)
            listener?.onProgress(total, total, "同步完成")
            result
        }
}
