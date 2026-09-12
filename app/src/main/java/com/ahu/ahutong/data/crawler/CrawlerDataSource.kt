package com.ahu.ahutong.data.crawler

import android.util.Log
import com.ahu.ahutong.data.AHUResponse
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
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPaymentRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomCurrentTimeRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayInfoRequest
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
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
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response
import kotlin.text.Regex

internal suspend fun fetchCurrentSemester(): CurrentSemester {
    val basicInfo = JwxtApi.API.fetchCourseTableBasicInfo()
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

internal fun extractCurrentSemesterJson(script: String): String? {
    val assignment = Regex("(?:var|let|const)\\s+currentSemester\\s*=\\s*")
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
    if (startIndex >= source.length || source[startIndex] != '{') return null
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
            '{' -> depth += 1
            '}' -> {
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

class CrawlerDataSource : BaseDataSource {

    val TAG = this::class.java.simpleName

    override suspend fun getSchedule(
        schoolYear: String,
        schoolTerm: String
    ): AHUResponse<List<Course>> {
        return AHUResponse<List<Course>>()
    }

    override suspend fun getSchedule(): AHUResponse<List<Course>> {
        val currentSemesterJson = fetchCurrentSemester()
        val courseTable = JwxtApi.API.getCourse(currentSemesterJson.id, currentSemesterJson.id)

        AHUCache.saveSchoolTerm(currentSemesterJson.name)

        return AHUResponse<List<Course>>().apply {
            data = courseTable.toCourseList()
            code = 0
            msg = ""
        }
    }

    override suspend fun getNextSchedule(): AHUResponse<List<Course>> {
        val currentSemesterJson = fetchCurrentSemester()
        val nextCourseTable = JwxtApi.API.getCourse(currentSemesterJson.id + 20, currentSemesterJson.id)

        return AHUResponse<List<Course>>().apply {
            data = nextCourseTable.toCourseList()
            code = 0
            msg = ""
        }
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

    override suspend fun getGrade(): AHUResponse<Grade> {
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
            .toMutableList()

        val response = AHUResponse<Grade>()
        val grade = Grade()
        grade.totalCredit = allGradeLists.sumOf {
            it.termTotalCredit?.toDoubleOrNull() ?: 0.0
        }.toString()
        grade.totalGradePoint = allGradeLists.sumOf {
            val avg = it.termGradePointAverage?.toDoubleOrNull() ?: 0.0
            val credit = it.termTotalCredit?.toDoubleOrNull() ?: 0.0
            avg * credit
        }.toString()
        val weightedGradePointSum = allGradeLists.sumOf {
            val avg = it.termGradePointAverage?.toDoubleOrNull() ?: 0.0
            val credit = it.termTotalCredit?.toDoubleOrNull() ?: 0.0
            avg * credit
        }
        grade.totalGradePointAverage = if (grade.totalCredit.toDouble() > 0) {
            "%.2f".format(weightedGradePointSum / grade.totalCredit.toDouble())
        } else {
            "0.0"
        }
        grade.termGradeList = allGradeLists
        response.data = grade
        response.code = 0
        // Cache per-profile grades for UI switching
        AHUCache.savePerProfileGrades(profiles.zip(perProfileGrades).toMap())
        return response
    }

    /**
     * Fetch and build a Grade object for a single student ID.
     * Returns null if no grade data exists for this ID.
     */
    suspend fun buildGradeForId(id: String): Grade? {
        val data = JwxtApi.API.getGrade(id)
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

        val grade = Grade()
        grade.totalCredit = termGradeLists.sumOf {
            it.termTotalCredit?.toDoubleOrNull() ?: 0.0
        }.toString()
        grade.totalGradePoint = termGradeLists.sumOf {
            val avg = it.termGradePointAverage?.toDoubleOrNull() ?: 0.0
            val credit = it.termTotalCredit?.toDoubleOrNull() ?: 0.0
            avg * credit
        }.toString()
        val weightedSum = termGradeLists.sumOf {
            val avg = it.termGradePointAverage?.toDoubleOrNull() ?: 0.0
            val credit = it.termTotalCredit?.toDoubleOrNull() ?: 0.0
            avg * credit
        }
        grade.totalGradePointAverage = if (grade.totalCredit.toDouble() > 0) {
            "%.2f".format(weightedSum / grade.totalCredit.toDouble())
        } else {
            "0.0"
        }
        grade.termGradeList = termGradeLists
        return grade
    }

    override suspend fun getGpaRankFromHtml(studentId: String): AHUResponse<GpaRankInfo> {
        val response = AHUResponse<GpaRankInfo>()
        val maskedStudentId = studentId.maskStudentId()
        Log.i(TAG, "getGpaRankFromHtml fallback start studentId=$maskedStudentId")
        try {
            val htmlResponse = JwxtApi.API.getGpaRankPage(studentId)
            Log.i(
                TAG,
                "getGpaRankFromHtml fallback http code=${htmlResponse.code()} " +
                    "success=${htmlResponse.isSuccessful} " +
                    "finalUrl=${htmlResponse.raw().request.url.toString().redactStudentId(studentId)}"
            )
            if (!htmlResponse.isSuccessful || htmlResponse.body() == null) {
                response.code = -1
                response.msg = "获取成绩排名页面失败"
                Log.w(TAG, "getGpaRankFromHtml fallback empty/non-success body studentId=$maskedStudentId")
                return response
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

            response.code = 0
            response.msg = "success"
            response.data = gpaRankInfo
            return response

        } catch (e: Exception) {
            Log.w(TAG, "getGpaRankFromHtml fallback failed studentId=$maskedStudentId", e)
            response.code = -1
            response.msg = "解析失败：${e.message}"
            return response
        }
    }

    /**
     * 将 JS 对象字符串 转换为 标准 JSON 字符串
     */
    private fun convertJsToJson(js: String): String {
        return js
            .replace(Regex("'"), "\"")                // 单引号 → 双引号
    }

    override suspend fun getAllCampus(): AHUResponse<AllCampus> {
        val response = AHUResponse<AllCampus>()
        try {
            // 直接请求 JSON 接口
            val campusList = AdwmhApi.API.getAllcampus()

            // 封装返回
            response.code = 0
            response.msg = "success"
            response.data = campusList
            return response

        } catch (e: Exception) {
            e.printStackTrace()
            response.code = -1
            response.msg = "解析校区列表失败：${e.message}"
            return response
        }
    }

    override suspend fun getAllLostFoundType(): AHUResponse<AllLostFoundType> {
        val response = AHUResponse<AllLostFoundType>()
        try {
            // 直接请求 JSON 接口
            val typeList = AdwmhApi.API.getAlllostfoundtype()
            // 封装返回
            response.code = 0
            response.msg = "success"
            response.data = typeList
            return response

        } catch (e: Exception) {
            e.printStackTrace()
            response.code = -1
            response.msg = "解析失败：${e.message}"
            return response
        }
    }

    override suspend fun getLostFoundList(
        pageNo: Int,
        pageSize: Int,
        state: Int
    ): AHUResponse<LostFoundResponse> {
        val response = AHUResponse<LostFoundResponse>()
        try {
            // 直接请求 JSON 接口
            val List = AdwmhApi.API.getLostFoundList(
                pageNo,
                pageSize,
                state
            )
            // 封装返回
            response.code = 0
            response.msg = "success"
            response.data = List
            return response

        } catch (e: Exception) {
            e.printStackTrace()
            response.code = -1
            response.msg = "解析失败：${e.message}"
            return response
        }
    }
    override suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AHUResponse<Any> {
        return AdwmhApi.API.publishLostFound(request)
    }
    override suspend fun deleteLostFound(
        id: String
    ): AHUResponse<Any> {
        return AdwmhApi.API.deleteLostFound(id)
    }

    override suspend fun getCardMoney(): AHUResponse<Card> {
        val card = Card()
        card.balance = AdwmhApi.API.getBalance().`object`
        val result = AHUResponse<Card>();
        result.data = card
        result.code = 0
        return result
    }

    override suspend fun getBathRooms(): AHUResponse<List<BathRoom>> {
        return AHUResponse<List<BathRoom>>().apply {
            code = -1
            msg = "浴室开放状态服务暂不可用"
            data = emptyList()
        }
    }

    override suspend fun getExamInfo(
        studentID: String,
        studentName: String
    ): AHUResponse<List<Exam>> {
        return try {
            val res = JwxtApi.API.fetchExamArrangePage()
            if (!res.isSuccessful || res.body() == null) {
                AHUResponse<List<Exam>>().apply {
                    code = -1
                    msg = "请求失败"
                    data = emptyList()
                }
            } else {
                val html = res.body()!!.string()

                // Try new HTML table format first (post-redesign: server-rendered <tr> elements)
                val tableExams = parseExamTableHtml(html)
                if (tableExams.isNotEmpty()) {
                    AHUResponse<List<Exam>>().apply {
                        code = 0
                        data = tableExams
                        msg = ""
                    }
                } else {
                    // Fallback: old format with studentExamInfoVms JS variable
                    val regex = Regex("(?s)studentExamInfoVms\\s*=\\s*(\\[.*?]);")
                    val match = regex.find(html)
                    if (match == null) {
                        AHUResponse<List<Exam>>().apply {
                            code = 0
                            msg = "未发现考试信息"
                            data = emptyList()
                        }
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
                        AHUResponse<List<Exam>>().apply {
                            code = 0
                            data = list
                            msg = ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AHUResponse<List<Exam>>().apply {
                code = -1
                msg = "解析失败: ${e.message}"
                data = emptyList()
            }
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
    ): AHUResponse<BathroomTelInfo> {

        val response = AHUResponse<BathroomTelInfo>()

        val (feeitemid, appId) = when (bathroom) {
            "竹园/龙河" -> "409" to "55"
            "桔园/蕙园" -> "430" to "56"

            else -> {
                response.code = -1
                response.msg = "目前没有这个浴室啊"
                response.data = null
                return response
            }
        }

        val initialization = YcardApi.initializeBathroomFeeItem(feeitemid, appId)
        if (!initialization.isSuccessful) {
            response.code = initialization.code()
            response.msg = "浴室缴费会话初始化失败"
            initialization.errorBody()?.close()
            return response
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

            bathroomInfo?.let {
                response.code = 0
                response.data = it
                response.msg = "success"
                return response
            }
            response.code = -1
            response.msg = "数据返回错误"

        } else {
            response.code = -1
            response.msg = "请求接口失败"
        }

        return response
    }

    override suspend fun getCardInfo(): AHUResponse<CardInfo> {
        val response = AHUResponse<CardInfo>()
        val result = YcardApi.authorizedCall { loadCardRecharge() }
        val body = result.body()
        if (result.isSuccessful && body != null) {
            response.data = body
            response.code = 0
            response.msg = "success"
        } else {
            response.code = result.code().takeIf { it != 0 } ?: -1
            response.msg = "校园卡信息加载失败：${result.message()}"
        }
        return response
    }

    override suspend fun getOrderThirdData(request: RequestBody): AHUResponse<Response<ResponseBody>> {
        val response = AHUResponse<Response<ResponseBody>>()
        response.data = YcardApi.authorizedCall { getOrderThirdData(request.toFormBody()) }
        response.code = if (response.data?.isSuccessful == true) 0 else -1
        response.msg = response.data?.message().orEmpty()
        return response
    }

    override suspend fun pay(request: RequestBody): AHUResponse<Response<ResponseBody>> {
        val response = AHUResponse<Response<ResponseBody>>()
        response.data = when (request) {
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
        response.code = if (response.data?.isSuccessful == true) 0 else -1
        response.msg = response.data?.message().orEmpty()
        return response
    }

    override suspend fun getSchoolCalendar(): AHUResponse<Response<ResponseBody>> {
        val response = AHUResponse<Response<ResponseBody>>()
        response.data = AhuTong.API.downloadFile("xiaoli.jpg");
        response.code = 0;
        return response
    }

    override suspend fun getSchoolCalendarYears(): AHUResponse<SchoolCalendarYearsResponse> {
        return AHUResponse<SchoolCalendarYearsResponse>().apply {
            data = AhuTong.API.getSchoolCalendarYears()
            code = 0
        }
    }

    override suspend fun getSchoolCalendar(year: String): AHUResponse<Response<ResponseBody>> {
        return AHUResponse<Response<ResponseBody>>().apply {
            data = AhuTong.API.getSchoolCalendar(year)
            code = if (data?.isSuccessful == true) 0 else -1
            msg = data?.message().orEmpty()
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
            val redirectUrl = JwxtApi.API.getGrade().raw().request.url.toString()
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
            val htmlResponse = JwxtApi.API.getGrade()
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
        val lastURL = JwxtApi.API.getGrade().raw().request.url.toString()
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
