package com.ahu.ahutong.data.crawler

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.base.BaseDataSource
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.jwxt.CourseTable
import com.ahu.ahutong.data.crawler.model.jwxt.CurrentSemester
import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPaymentRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomCurrentTimeRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayInfoRequest
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.crawler.utils.GpaRankHtmlParser
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.schedule.ScheduleSemester
import kotlinx.coroutines.CancellationException
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response
import kotlin.text.Regex
import com.ahu.ahutong.data.crawler.model.adwnh.toAhuResult
import com.ahu.ahutong.data.toAhuError

internal suspend fun fetchCurrentSemester(jwxt: JwxtApi): CurrentSemester {
    val basicInfo = jwxt.fetchCourseTableBasicInfo()
    val body = basicInfo.body()?.string()
        ?: throw IllegalStateException("Cannot load current semester page")
    val currentSemesterJson = Jsoup.parse(body)
        .select("script")
        .asSequence()
        .map { it.data() }
        .mapNotNull(::extractCurrentSemesterJson)
        .firstOrNull()
        ?: throw IllegalStateException("Cannot parse current semester")

    return Gson().fromJson(
        currentSemesterJson,
        CurrentSemester::class.java
    )
}

internal fun extractCurrentSemesterJson(script: String): String? =
    extractAssignedSemesterJson(script, "currentSemester")?.takeIf { it.trimStart().startsWith('{') }

internal fun extractAssignedSemesterJson(script: String, variable: String): String? {
    val assignment = Regex("(?:var|let|const)\\s+${Regex.escape(variable)}\\s*=\\s*")
        .find(script)
        ?: return null
    var cursor = assignment.range.last + 1
    while (cursor < script.length && script[cursor].isWhitespace()) cursor += 1

    if (script.startsWith("JSON.parse", cursor)) {
        cursor += "JSON.parse".length
        while (cursor < script.length && script[cursor].isWhitespace()) cursor += 1
        if (cursor >= script.length || script[cursor] != '(') return null
        cursor += 1
        while (cursor < script.length && script[cursor].isWhitespace()) cursor += 1
        val parsed = parseJavaScriptString(script, cursor) ?: return null
        cursor = parsed.nextIndex
        while (cursor < script.length && script[cursor].isWhitespace()) cursor += 1
        return parsed.value.takeIf { cursor < script.length && script[cursor] == ')' }
    }

    return extractBalancedJsonObject(script, cursor)
}

private fun extractBalancedJsonObject(source: String, startIndex: Int): String? {
    if (startIndex >= source.length || source[startIndex] !in charArrayOf('{', '[')) return null
    var depth = 0
    var quote: Char? = null
    var escaped = false
    for (index in startIndex until source.length) {
        val character = source[index]
        if (quote != null) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> quote = null
            }
            continue
        }
        when (character) {
            '\'', '"' -> quote = character
            '{', '[' -> depth += 1
            '}', ']' -> {
                depth -= 1
                if (depth == 0) return source.substring(startIndex, index + 1)
                if (depth < 0) return null
            }
        }
    }
    return null
}

private data class ParsedJavaScriptString(val value: String, val nextIndex: Int)

private fun parseJavaScriptString(source: String, startIndex: Int): ParsedJavaScriptString? {
    if (startIndex >= source.length || source[startIndex] !in charArrayOf('\'', '"')) return null
    val quote = source[startIndex]
    val result = StringBuilder()
    var cursor = startIndex + 1
    while (cursor < source.length) {
        val character = source[cursor++]
        if (character == quote) return ParsedJavaScriptString(result.toString(), cursor)
        if (character != '\\') {
            result.append(character)
            continue
        }
        if (cursor >= source.length) return null
        when (val escaped = source[cursor++]) {
            '\\', '\'', '"', '/' -> result.append(escaped)
            'b' -> result.append('\b')
            'f' -> result.append('\u000C')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'u' -> {
                if (cursor + 4 > source.length) return null
                val codePoint = source.substring(cursor, cursor + 4).toIntOrNull(16) ?: return null
                result.append(codePoint.toChar())
                cursor += 4
            }
            'x' -> {
                if (cursor + 2 > source.length) return null
                val codePoint = source.substring(cursor, cursor + 2).toIntOrNull(16) ?: return null
                result.append(codePoint.toChar())
                cursor += 2
            }
            '\n' -> Unit
            '\r' -> if (cursor < source.length && source[cursor] == '\n') cursor += 1
            else -> result.append(escaped)
        }
    }
    return null
}

class CrawlerDataSource(
    /** 教务系统协议客户端；默认生产实例，契约测试可注入指向本地 fixture 服务的实例。 */
    private val jwxt: JwxtApi = JwxtApi.API,
    /** 安大智慧协议客户端；同上。 */
    private val adwmh: AdwmhApi = AdwmhApi.API,
    /** 测试可注入原始教务响应；生产只从共享协议接口提取学期元数据。 */
    private val semesterResponseProvider: (suspend () -> List<GradeResponse>)? = null
) : BaseDataSource {

    val TAG = this::class.java.simpleName

    override suspend fun getSchedule(
        schoolYear: String,
        schoolTerm: String
    ): AhuResult<List<Course>> {
        // 历史遗留重载：从未实现，旧行为是"code 保持默认 -1 的失败结果"。
        return AhuResult.Failure(AhuError.Server(-1, ""))
    }

    override suspend fun getSchedule(): AhuResult<List<Course>> {
        val currentSemesterJson = fetchCurrentSemester(jwxt)
        val courseTable = jwxt.getCourse(currentSemesterJson.id, currentSemesterJson.id)

        AHUCache.saveSchoolTerm(currentSemesterJson.name)

        return AhuResult.Success(courseTable.toCourseList())
    }

    override suspend fun getNextSchedule(): AhuResult<List<Course>> {
        val currentSemesterJson = fetchCurrentSemester(jwxt)
        val nextCourseTable = jwxt.getCourse(currentSemesterJson.id + 20, currentSemesterJson.id)

        return AhuResult.Success(nextCourseTable.toCourseList())
    }

    override suspend fun getScheduleSemesters(): AhuResult<List<ScheduleSemester>> {
        val response = jwxt.fetchCourseTableBasicInfo()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            response.body()?.close()
            return AhuResult.Failure(AhuError.Server(response.code(), "获取学期目录失败"))
        }
        val html = response.body()?.string()
            ?: return AhuResult.Failure(AhuError.ProtocolChanged("教务学期目录为空"))
        val catalog = ScheduleSemesterParser.parse(html)
        val semesters = catalog.semesters.associateByTo(linkedMapOf()) { it.id }

        // 共享教务协议响应含真实学期目录，只解析 ID/名称，不经过成绩业务或缓存。
        // 读取全部已知培养方案，不能因课表页已有一个历史选项而跳过。
        readSemesterDirectoryResponses().flatMap(ScheduleSemesterParser::fromGrades).forEach { semester ->
            val existing = semesters[semester.id]
            if (existing == null || existing.name == "学期 ${semester.id}") {
                semesters[semester.id] = semester
            }
        }

        // 当前学期来自课表页，覆盖补充目录的名字和 current 标记。
        semesters[catalog.current.id] = catalog.current
        return AhuResult.Success(ScheduleSemesterParser.sort(semesters.values.toList()))
    }

    private suspend fun readSemesterDirectoryResponses(): List<GradeResponse> {
        semesterResponseProvider?.let { provider ->
            return optionalSemesterLookup { provider() }.orEmpty()
        }
        return optionalSemesterLookup { getGradeStudentProfiles() }.orEmpty().mapNotNull { profile ->
            // 某个培养方案失败时仍保留其余方案返回的真实学期 ID。
            optionalSemesterLookup { jwxt.getGrade(profile.id) }
        }
    }

    override suspend fun getSemesterSchedule(semesterId: Int): AhuResult<List<Course>> {
        if (semesterId <= 0) {
            return AhuResult.Failure(AhuError.ProtocolChanged("无效的学期 ID"))
        }
        val currentSemester = fetchCurrentSemester(jwxt)
        // 与既有下学期入口保持一致：路径指定目标学期，查询参数保留当前学期上下文。
        // 已用真实账号验证历史课表请求；可用历史 ID 仍只取服务端目录，不由参数关系推算。
        return AhuResult.Success(jwxt.getCourse(semesterId, currentSemester.id).toCourseList())
    }

    private suspend fun <T> optionalSemesterLookup(block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Supplementary semester directory unavailable")
        null
    }

    private fun CourseTable.toCourseList(): List<Course> {
        val courseList = ArrayList<Course>()
        studentTableVms.firstOrNull()?.activities.orEmpty().forEach {
            val sortedWeekIndexes = it.weekIndexes.sorted()
            if (sortedWeekIndexes.isEmpty()) {
                return@forEach
            }

            val course = Course()
            course.name = it.courseName
            course.setStartWeek(sortedWeekIndexes.first().toString())
            course.setLength((it.endUnit - it.startUnit + 1).toString())
            course.setWeekday(it.weekday.toString())
            course.setEndWeek(sortedWeekIndexes.last().toString())
            course.setStartTime(it.startUnit.toString())
            course.location = it.room ?: "未知"
            course.teacher = it.teacherNames.joinToString(", ")
            course.weekIndexes = sortedWeekIndexes
            course.courseId = it.lessonId.toString()

            Log.e(TAG, "getSchedule: $course")
            courseList.add(course)
        }
        return courseList
    }

    override suspend fun getGrade(): AhuResult<Grade> {
        val profiles = getGradeStudentProfiles()

        // Fetch grades for each profile ID, build individual Grade objects
        val perProfileGrades = profiles.map { profile ->
            try {
                buildGradeForId(profile.id)
            } catch (e: Exception) {
                Log.w(TAG, "getGrade failed for id=${profile.id}", e)
                null
            }
        }

        // Merge all profile grades into one combined Grade
        val allGradeLists = perProfileGrades
            .filterNotNull()
            .flatMap { it.termGradeList ?: emptyList() }

        // Cache per-profile grades for UI switching
        AHUCache.savePerProfileGrades(profiles.zip(perProfileGrades).toMap())
        return AhuResult.Success(GradeMapper.aggregate(allGradeLists))
    }

    /**
     * Fetch and build a Grade object for a single student ID.
     * Returns null if no grade data exists for this ID.
     */
    suspend fun buildGradeForId(id: String): Grade? {
        val data = jwxt.getGrade(id)
        val termGradeLists = mutableListOf<Grade.TermGradeListBean>()

        data.semesterId2studentGrades?.values?.forEach { gradeList ->
            var termName: String? = null
            val newGradeList = mutableListOf<Grade.TermGradeListBean.GradeListBean>()

            gradeList.forEach { it ->
                termName = termName ?: it.semesterName
                val grade = Grade.TermGradeListBean.GradeListBean()
                grade.course = it.courseName
                grade.credit = it.credits.toString()
                grade.grade = it.gaGrade
                grade.gradePoint = it.gp.toString()
                grade.courseNature = it.courseType
                grade.courseNum = it.courseCode
                grade.semesterId = it.semesterId!!
                grade.gradeDetail = it.gradeDetail
                newGradeList.add(grade)
            }

            termName?.let { name ->
                val names = name.split("-")
                if (names.size < 3) return@forEach

                val termGradeList = Grade.TermGradeListBean()
                termGradeList.gradeList = newGradeList
                termGradeList.term = names[2]
                termGradeList.schoolYear = "${names[0]}-${names[1]}"
                termGradeList.termGradePoint = newGradeList.sumOf { itt ->
                    itt.grade?.toDoubleOrNull() ?: 0.0
                }.toString()
                termGradeList.termTotalCredit = newGradeList.sumOf { itt ->
                    itt.credit?.toDoubleOrNull() ?: 0.0
                }.toString()
                val totalGradePointWeighted = newGradeList.sumOf {
                    (it.gradePoint?.toDoubleOrNull() ?: 0.0) * (it.credit?.toDoubleOrNull() ?: 0.0)
                }
                termGradeList.termGradePointAverage =
                    if (termGradeList.termTotalCredit.toDouble() > 0) {
                        "%.2f".format(totalGradePointWeighted / termGradeList.termTotalCredit.toDouble())
                    } else {
                        "0.0"
                    }
                termGradeLists.add(termGradeList)
            }
        }

        if (termGradeLists.isEmpty()) return null

        return GradeMapper.aggregate(termGradeLists)
    }

    override suspend fun getGpaRankFromHtml(studentId: String): AhuResult<GpaRankInfo> {
        val maskedStudentId = studentId.maskStudentId()
        Log.i(TAG, "getGpaRankFromHtml fallback start studentId=$maskedStudentId")
        try {
            val htmlResponse = jwxt.getGpaRankPage(studentId)
            Log.i(
                TAG,
                "getGpaRankFromHtml fallback http code=${htmlResponse.code()} " +
                    "success=${htmlResponse.isSuccessful} " +
                    "finalUrl=${htmlResponse.raw().request.url.toString().redactStudentId(studentId)}"
            )
            if (!htmlResponse.isSuccessful || htmlResponse.body() == null) {
                Log.w(TAG, "getGpaRankFromHtml fallback empty/non-success body studentId=$maskedStudentId")
                return AhuResult.Failure(AhuError.Server(-1, "获取成绩排名页面失败"))
            }

            val html = htmlResponse.body()!!.string()
            Log.i(
                TAG,
                "getGpaRankFromHtml fallback html length=${html.length} " +
                    "hasModel=${GpaRankHtmlParser.hasModelAssignment(html)} " +
                    "looksLogin=${html.contains("cas/login", ignoreCase = true) || html.contains("tologin", ignoreCase = true)}"
            )
            val jsObject = GpaRankHtmlParser.extractModelObject(html)
            Log.i(TAG, "getGpaRankFromHtml fallback model extracted length=${jsObject.length}")

            val json = convertJsToJson(jsObject)
            val gpaRankInfo = Gson().fromJson(json, GpaRankInfo::class.java)
            Log.i(
                TAG,
                "getGpaRankFromHtml fallback parsed gpa=${gpaRankInfo.gpa} " +
                    "rank=${gpaRankInfo.majorRank}/${gpaRankInfo.majorHeadCount} " +
                    "semesters=${gpaRankInfo.gpaSemesterSubs.size}"
            )

            return AhuResult.Success(gpaRankInfo)

        } catch (e: Exception) {
            Log.w(TAG, "getGpaRankFromHtml fallback failed studentId=$maskedStudentId", e)
            return AhuResult.Failure(AhuError.ProtocolChanged("解析失败：${e.message}"))
        }
    }

    /**
     * 将 JS 对象字符串 转换为 标准 JSON 字符串
     */
    private fun convertJsToJson(js: String): String {
        return js
            .replace(Regex("'"), "\"")                // 单引号 → 双引号
    }

    override suspend fun getAllCampus(): AhuResult<AllCampus> {
        try {
            // 直接请求 JSON 接口
            val campusList = adwmh.getAllcampus()

            return AhuResult.Success(campusList)

        } catch (e: Exception) {
            return AhuResult.Failure(e.toAhuError())
        }
    }

    override suspend fun getAllLostFoundType(): AhuResult<AllLostFoundType> {
        try {
            // 直接请求 JSON 接口
            val typeList = adwmh.getAlllostfoundtype()
            return AhuResult.Success(typeList)

        } catch (e: Exception) {
            return AhuResult.Failure(e.toAhuError())
        }
    }

    override suspend fun getLostFoundList(
        pageNo: Int,
        pageSize: Int,
        state: Int
    ): AhuResult<LostFoundResponse> {
        try {
            // 直接请求 JSON 接口
            val List = adwmh.getLostFoundList(
                pageNo,
                pageSize,
                state
            )
            return AhuResult.Success(List)

        } catch (e: Exception) {
            return AhuResult.Failure(e.toAhuError())
        }
    }
    override suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AhuResult<Any> {
        return adwmh.publishLostFound(request).toAhuResult()
    }
    override suspend fun deleteLostFound(
        id: String
    ): AhuResult<Any> {
        return adwmh.deleteLostFound(id).toAhuResult()
    }

    override suspend fun getCardMoney(): AhuResult<Card> {
        val response = adwmh.getBalance()
        if (response.code != 10000) {
            // 登录失效常以 HTTP 200 + 业务错误码返回；只有 401 才是明确鉴权信号，
            // 其余错误码（服务繁忙/风控/上游故障）不能被上层当成会话失效。
            val error = if (response.code == 401) {
                AhuError.Unauthorized(response.msg.ifBlank { "校园卡登录已失效" })
            } else {
                AhuError.Server(response.code, response.msg)
            }
            return AhuResult.Failure(error)
        }
        val card = Card()
        card.balance = response.`object`
        return AhuResult.Success(card)
    }

    override suspend fun getBathRooms(): AhuResult<List<BathRoom>> {
        return AhuResult.Failure(AhuError.Server(-1, "浴室开放状态服务暂不可用"))
    }

    override suspend fun getExamInfo(
        studentID: String,
        studentName: String
    ): AhuResult<List<Exam>> {
        return try {
            val res = jwxt.fetchExamArrangePage()
            if (!res.isSuccessful || res.body() == null) {
                AhuResult.Failure(AhuError.Server(-1, "请求失败"))
            } else {
                val html = res.body()!!.string()

                // Try new HTML table format first (post-redesign: server-rendered <tr> elements)
                val tableExams = parseExamTableHtml(html)
                if (tableExams.isNotEmpty()) {
                    AhuResult.Success(tableExams)
                } else {
                    // Fallback: old format with studentExamInfoVms JS variable
                    val regex = Regex("(?s)studentExamInfoVms\\s*=\\s*(\\[.*?]);")
                    val match = regex.find(html)
                    if (match == null) {
                        AhuResult.Success(emptyList())
                    } else {
                        val jsonStr = match.groupValues[1]
                        val fixedJson = jsonStr.replace("'", "\"")
                        val jsonArray = JsonParser.parseString(fixedJson).asJsonArray
                        val list = mutableListOf<Exam>()
                        jsonArray.forEach { elem ->
                            val obj = elem.asJsonObject
                            val courseObj = obj.getAsJsonObject("course")
                            val examTypeObj = obj.getAsJsonObject("examType")
                            val courseName = courseObj?.get("nameZh")?.asString ?: ""
                            val examTypeName = examTypeObj?.get("nameZh")?.asString ?: ""
                            val courseDisplay = if (examTypeName.isNotEmpty()) "$courseName($examTypeName)" else courseName
                            val time = obj.get("examTime")?.asString ?: ""
                            val seatVal = obj.get("seatNo")
                            val seatNum = when {
                                seatVal == null || seatVal.isJsonNull -> ""
                                seatVal.isJsonPrimitive && seatVal.asJsonPrimitive.isNumber -> seatVal.asNumber.toString()
                                else -> seatVal.asString
                            }
                            val campus = obj.getAsJsonObject("requiredCampus")?.get("nameZh")?.asString ?: ""
                            val room = obj.get("room")?.asString ?: ""
                            val location = if (campus.isNotEmpty() && room.isNotEmpty()) "$campus-$room" else campus + room
                            val finished = obj.get("finished")?.asBoolean ?: false
                            val exam = Exam().apply {
                                setCourse(courseDisplay)
                                setTime(time)
                                setSeatNum(seatNum)
                                setLocation(location)
                                setFinished(finished)
                            }
                            list.add(exam)
                        }
                        AhuResult.Success(list)
                    }
                }
            }
        } catch (e: Exception) {
            AhuResult.Failure(AhuError.ProtocolChanged("解析失败: ${e.message}"))
        }
    }

    /**
     * Parse exam info from the new server-rendered HTML table format.
     * The page renders exams as <tr> elements with seat data in a JS variable studentExamList.
     */
    private fun parseExamTableHtml(html: String): List<Exam> {
        // 1. Parse studentExamList for seat number mapping (exam id -> seat number)
        val seatMap = mutableMapOf<String, String>()
        val seatListRegex = Regex("(?s)var\\s+studentExamList\\s*=\\s*(\\[.+?\\]);")
        seatListRegex.find(html)?.let { match ->
            val jsonStr = match.groupValues[1].replace("'", "\"")
            try {
                val arr = JsonParser.parseString(jsonStr).asJsonArray
                arr.forEach {
                    val obj = it.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("id")?.asLong?.toString() ?: ""
                    val seat = obj.get("seatNo")?.asString ?: obj.get("seatNo")?.asLong?.toString() ?: ""
                    if (id.isNotEmpty()) seatMap[id] = seat
                }
            } catch (_: Exception) { }
        }

        // 2. Parse HTML table rows
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr[data-finished]")
        if (rows.isEmpty()) return emptyList()

        return rows.map { row ->
            val finished = row.attr("data-finished") == "true"

            // Time from <div class="time ...">
            val time = row.select("div.time").first()?.text()?.trim() ?: ""

            // Course name from bold <span>
            val course = row.select("span[style*=font-weight]").firstOrNull { el ->
                el.attr("style").contains("bold")
            }?.text()?.trim() ?: ""

            // Exam type from <span class="tag-span typeX">
            val examType = row.select("span.tag-span").first()?.text()?.trim() ?: ""

            // Seat exam ID from <span id="seat-NNN">
            val seatId = row.select("span[id^=seat-]").first()?.id()?.removePrefix("seat-") ?: ""
            val seatNum = seatMap[seatId] ?: ""

            // Location: campus, building, room from spans in first <td>
            val firstTd = row.select("td").first()
            val locationSpans = firstTd?.select("span")?.filter {
                !it.id().startsWith("seat-") && it.text().trim().isNotEmpty()
            } ?: emptyList()
            val location = locationSpans.joinToString("-") { it.text().trim() }

            val courseDisplay = if (examType.isNotEmpty()) "$course($examType)" else course

            Exam().apply {
                setCourse(courseDisplay)
                setTime(time)
                setSeatNum(seatNum)
                setLocation(location)
                setFinished(finished)
            }
        }
    }

    override suspend fun getBathroomTelInfo(
        bathroom: String,
        tel: String
    ): AhuResult<BathroomTelInfo> {
        val (feeitemid, appId) = when (bathroom) {
            "竹园/龙河" -> "409" to "55"
            "桔园/蕙园" -> "430" to "56"

            else -> return AhuResult.Failure(AhuError.Server(-1, "目前没有这个浴室啊"))
        }

        val initialization = YcardApi.initializeBathroomFeeItem(feeitemid, appId)
        if (!initialization.isSuccessful) {
            initialization.errorBody()?.close()
            return AhuResult.Failure(
                AhuError.Server(initialization.code(), "浴室缴费会话初始化失败")
            )
        }
        initialization.body()?.close()


        val formBody = FormBody.Builder()
            .add("feeitemid", feeitemid)
            .add("type", "IEC")
            .add("level", "1")
            .add("telPhone", tel)
            .build()


        val res = YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
            getFeeItemThirdData(formBody)
        }

        if (res.isSuccessful) {
            val responseBody = res.body()
            val responseJson = responseBody?.string()

            val bathroomInfo = Gson().fromJson(responseJson, BathroomTelInfo::class.java)

            bathroomInfo?.let { return AhuResult.Success(it) }
            return AhuResult.Failure(AhuError.Server(-1, "数据返回错误"))
        }

        return res.toClosedFailure("请求接口失败")
    }

    override suspend fun getCardInfo(): AhuResult<CardInfo> {
        val result = YcardApi.authorizedCall { loadCardRecharge() }
        val body = result.body()
        return if (result.isSuccessful && body != null) {
            AhuResult.Success(body)
        } else {
            result.toClosedFailure("校园卡信息加载失败：${result.message()}")
        }
    }


    override suspend fun getBillPage(
        page: Int,
        size: Int,
        timeFrom: String?,
        timeTo: String?,
        type: Int?
    ): AhuResult<TurnoverPage> {
        val result = YcardApi.authorizedCall {
            getTurnover(size = size, current = page, timeFrom = timeFrom, timeTo = timeTo, type = type)
        }
        val body = result.body()?.data
        return if (result.isSuccessful && body != null) {
            AhuResult.Success(body)
        } else {
            result.toClosedFailure("账单加载失败：${result.message()}")
        }
    }

    override suspend fun getBillSummary(timeFrom: String, timeTo: String): AhuResult<TurnoverCount> {
        val result = YcardApi.authorizedCall { getTurnoverCount(timeFrom = timeFrom, timeTo = timeTo) }
        val body = result.body()?.data
        return if (result.isSuccessful && body != null) {
            AhuResult.Success(body)
        } else {
            result.toClosedFailure("账单汇总加载失败：${result.message()}")
        }
    }

    override suspend fun getOrderThirdData(request: RequestBody): AhuResult<Response<ResponseBody>> {
        val call = YcardApi.authorizedCall { getOrderThirdData(request.toFormBody()) }
        return if (call.isSuccessful) {
            AhuResult.Success(call)
        } else {
            call.toClosedFailure()
        }
    }

    override suspend fun pay(request: RequestBody): AhuResult<Response<ResponseBody>> {
        val call = when (request) {
            is BathroomPayInfoRequest -> YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
                getPayInfo(request.orderId)
            }
            is BathroomCurrentTimeRequest -> YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
                getCurrentTime()
            }
            is BathroomPaymentRequest -> YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
                pay(request.toFormBody())
            }
            else -> YcardApi.authorizedCall { pay(request.toFormBody()) }
        }
        return if (call.isSuccessful) {
            AhuResult.Success(call)
        } else {
            call.toClosedFailure()
        }
    }

    override suspend fun getSchoolCalendar(): AhuResult<Response<ResponseBody>> {
        return AhuResult.Success(AhuTong.API.downloadFile("xiaoli.jpg"))
    }

    /** 校历年份目录（master 新增的历史校历功能），错误语义按本分支的统一模型表达。 */
    override suspend fun getSchoolCalendarYears(): AhuResult<SchoolCalendarYearsResponse> {
        return AhuResult.Success(AhuTong.API.getSchoolCalendarYears())
    }

    override suspend fun getSchoolCalendar(year: String): AhuResult<Response<ResponseBody>> {
        val response = AhuTong.API.getSchoolCalendar(year)
        return if (response.isSuccessful) {
            AhuResult.Success(response)
        } else {
            response.toClosedFailure()
        }
    }


    /**
     * Parse student profiles from the grade sheet HTML page.
     * For students with micro-majors/minors, there may be multiple profiles.
     * Each .student-panel-block contains: trainingType, department, major, and a button with the ID.
     */
    private fun parseGradeStudentProfiles(html: String): List<GradeStudentProfile> {
        val doc = Jsoup.parse(html)
        val panels = doc.select(".student-panel-block")
        Log.i(TAG, "parseGradeStudentProfiles panels=${panels.size}")
        if (panels.isEmpty()) return emptyList()

        return panels.mapNotNull { panel ->
            val button = panel.select("button[onclick*=myFunction]").first()
            val id = button?.attr("value")?.takeIf { it.isNotBlank() }
                ?: button?.attr("onclick")?.let { Regex("""(\d+)""").find(it)?.value }
                ?: return@mapNotNull null

            val dds = panel.select("dd")
            val trainingType = dds.getOrNull(0)?.text()?.trim() ?: ""
            val department = dds.getOrNull(1)?.text()?.trim() ?: ""
            val major = dds.getOrNull(2)?.text()?.trim() ?: ""

            GradeStudentProfile(
                id = id,
                trainingType = trainingType,
                department = department,
                major = major
            )
        }
    }

    /**
     * Get all student profiles for grade fetching.
     * - First tries to get single ID from redirect (legacy path for students without micro-major)
     * - Falls back to parsing HTML for multi-panel page
     * Results are cached in AHUCache.
     */
    suspend fun getGradeStudentProfiles(): List<GradeStudentProfile> {
        // Check cache first
        val cached = AHUCache.getGradeStudentProfiles()
        if (cached.isNotEmpty()) {
            Log.i(TAG, "getGradeStudentProfiles cache size=${cached.size}")
            return cached
        }

        // Try legacy redirect approach (single ID, no micro-major)
        try {
            val redirectUrl = jwxt.getGrade().raw().request.url.toString()
            val lastSegment = redirectUrl.split("/").last()
            Log.i(
                TAG,
                "getGradeStudentProfiles redirect last=${lastSegment.maskStudentId()}"
            )
            if (lastSegment.toIntOrNull() != null) {
                // Redirect worked - single student, no multi-panel
                val list = listOf(GradeStudentProfile(
                    id = lastSegment,
                    trainingType = "主修",
                    department = "",
                    major = "本专业"
                ))
                AHUCache.setGradeStudentProfiles(list)
                // Also set legacy ID for backward compat
                AHUCache.setJwxtStudentId(lastSegment)
                return list
            }
        } catch (e: Exception) {
            Log.w(TAG, "getGradeStudentProfiles redirect failed", e)
        }

        // Redirect didn't work - parse HTML for multi-panel
        try {
            val htmlResponse = jwxt.getGrade()
            Log.i(
                TAG,
                "getGradeStudentProfiles html http code=${htmlResponse.code()} " +
                    "success=${htmlResponse.isSuccessful} finalUrl=${htmlResponse.raw().request.url}"
            )
            if (htmlResponse.isSuccessful && htmlResponse.body() != null) {
                val html = htmlResponse.body()!!.string()
                val profiles = parseGradeStudentProfiles(html)
                Log.i(
                    TAG,
                    "getGradeStudentProfiles html length=${html.length} parsed=${profiles.size} " +
                        "looksLogin=${html.contains("cas/login", ignoreCase = true) || html.contains("tologin", ignoreCase = true)}"
                )
                if (profiles.isNotEmpty()) {
                    AHUCache.setGradeStudentProfiles(profiles)
                    // Also set first ID as legacy for backward compat
                    AHUCache.setJwxtStudentId(profiles.first().id)
                    return profiles
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse grade student profiles", e)
        }

        Log.w(TAG, "getGradeStudentProfiles empty")
        return emptyList()
    }

    /**
     * Legacy method, kept for backward compatibility.
     * Use getGradeStudentProfiles() for new code.
     */
    suspend fun getStudentId(): String {
        val profiles = getGradeStudentProfiles()
        if (profiles.isNotEmpty()) return profiles.first().id
        // This should rarely happen
        val lastURL = jwxt.getGrade().raw().request.url.toString()
        val data = lastURL.split("/")
        return data.last()
    }

    private fun String.maskStudentId(): String {
        if (length <= 4) return "****"
        return take(2) + "***" + takeLast(2)
    }

    private fun String.redactStudentId(studentId: String): String {
        if (studentId.isBlank()) return this
        return replace(studentId, studentId.maskStudentId())
    }
}
