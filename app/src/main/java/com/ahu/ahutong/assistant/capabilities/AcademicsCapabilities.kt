package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantCardItem
import com.ahu.ahutong.assistant.AssistantCardStat
import com.ahu.ahutong.assistant.AssistantCardTone
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.EvaluationRepository
import com.ahu.ahutong.data.ProgramCompletionGateway
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.session.SessionStore
import com.google.gson.JsonObject

/**
 * 教务类只读能力：成绩、绩点排名、考试、培养方案、评教待办。
 * 全部复用既有 repository / gateway，不做额外的网络请求编排。
 */
class GetGradesCapability(
    private val load: suspend () -> AhuResult<Grade> = { AHURepository.getGrade(isRefresh = false) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_grades",
        title = "查询成绩",
        description = "查询成绩。term 不传时返回各学期汇总；传学期（例如 2025-2026 或 上学期）时返回该学期课程成绩。",
        parameters = schema("term" to stringProp("学期名称或关键字，例如 2025-2026 学年第一学期")),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "教务系统",
        openRoute = "grade",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        return when (val result = load()) {
            is AhuResult.Failure -> result.error.asAssistantFailure()
            is AhuResult.Success -> {
                val grade = result.value
                val terms = grade.termGradeList.orEmpty()
                if (terms.isEmpty()) {
                    return message("还没有可用的成绩数据，打开成绩页刷新一次")
                }
                val keyword = arguments.stringArg("term")
                val selected = if (keyword == null) null else terms.firstOrNull {
                    matchesTerm(it.schoolYear.orEmpty(), it.term.orEmpty(), keyword)
                }
                if (keyword != null && selected == null) {
                    return message("没有找到「$keyword」的成绩，打开成绩页看看有哪些学期")
                }
                if (selected == null) {
                    val lines = terms.map { term ->
                        buildString {
                            append(term.schoolYear.orEmpty())
                            append(" 第")
                            append(term.term.orEmpty())
                            append("学期 · 绩点 ")
                            append(term.termGradePointAverage.orEmpty().ifBlank { "-" })
                            append(" · ")
                            append(term.termTotalCredit.orEmpty().ifBlank { "0" })
                            append(" 学分")
                        }
                    }
                    AssistantOutcome.Success(
                        AssistantAnswer(
                            summary = "共 ${terms.size} 个学期，平均绩点 " +
                                grade.totalGradePointAverage.orEmpty().ifBlank { "-" },
                            lines = lines,
                            route = spec.openRoute,
                            card = AssistantCard(
                                kind = "grades",
                                headline = grade.totalGradePointAverage.orEmpty().ifBlank { "-" },
                                headlineSuffix = "平均绩点",
                                stats = listOf(
                                    AssistantCardStat("学期数", terms.size.toString()),
                                    AssistantCardStat("总学分", grade.totalCredit.orEmpty().ifBlank { "-" }),
                                ),
                                items = terms.map { term ->
                                    AssistantCardItem(
                                        title = "${term.schoolYear.orEmpty()} 第${term.term.orEmpty()}学期",
                                        subtitle = "${term.termTotalCredit.orEmpty().ifBlank { "0" }} 学分",
                                        value = "绩点 ${term.termGradePointAverage.orEmpty().ifBlank { "-" }}",
                                    )
                                },
                            ),
                        )
                    )
                } else {
                    val courses = selected.gradeList.orEmpty()
                    val credits = courses.mapNotNull { it.credit?.toDoubleOrNull() }.sum()
                    val lines = courses.take(MAX_COURSES).map { course ->
                        buildString {
                            append(course.course.orEmpty())
                            append(" · ")
                            append(course.grade.orEmpty().ifBlank { "-" })
                            append(" · ")
                            append(course.credit.orEmpty().ifBlank { "0" })
                            append(" 学分")
                            course.gradePoint?.takeIf { it.isNotBlank() }?.let {
                                append(" · 绩点 ")
                                append(it)
                            }
                        }
                    }
                    val more = (courses.size - lines.size).takeIf { it > 0 }?.let { "…还有 $it 门" }
                    AssistantOutcome.Success(
                        AssistantAnswer(
                            summary = "${selected.schoolYear.orEmpty()} 第${selected.term.orEmpty()}学期 " +
                                "${courses.size} 门课程，绩点 " +
                                selected.termGradePointAverage.orEmpty().ifBlank { "-" },
                            lines = lines + listOfNotNull(more),
                            route = spec.openRoute,
                            card = AssistantCard(
                                kind = "grades",
                                headline = selected.termGradePointAverage.orEmpty().ifBlank { "-" },
                                headlineSuffix = "绩点",
                                subtitle = "${selected.schoolYear.orEmpty()} 第${selected.term.orEmpty()}学期",
                                stats = listOf(
                                    AssistantCardStat("课程数", courses.size.toString()),
                                    AssistantCardStat("学分", formatCredit(credits)),
                                ),
                                items = courses.take(MAX_CARD_COURSES).map { course ->
                                    AssistantCardItem(
                                        title = course.course.orEmpty(),
                                        subtitle = "${course.credit.orEmpty().ifBlank { "0" }} 学分",
                                        value = course.grade.orEmpty().ifBlank { "-" },
                                        status = course.gradePoint?.takeIf { it.isNotBlank() }?.let { "绩点 $it" },
                                        tone = AssistantCardTone.ACCENT,
                                    )
                                },
                            ),
                        )
                    )
                }
            }
        }
    }

    private fun message(text: String): AssistantOutcome.Success =
        AssistantOutcome.Success(AssistantAnswer(summary = text, route = spec.openRoute))

    private fun matchesTerm(schoolYear: String, term: String, keyword: String): Boolean {
        val normalized = keyword.replace(" ", "")
        val upper = normalized.contains("上") || normalized.contains("第一")
        val lower = normalized.contains("下") || normalized.contains("第二")
        val yearMatches = schoolYear.isNotBlank() && normalized.contains(schoolYear)
        if (yearMatches) {
            return when {
                upper -> term == "1" || term.contains("一")
                lower -> term == "2" || term.contains("二")
                else -> true
            }
        }
        val label = "$schoolYear-$term"
        return when {
            upper -> term == "1" || term.contains("一")
            lower -> term == "2" || term.contains("二")
            else -> label.contains(normalized)
        }
    }

    private fun formatCredit(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    private companion object {
        const val MAX_COURSES = 20
        const val MAX_CARD_COURSES = 40
    }
}

class GetGpaRankCapability(
    private val currentStudentId: () -> String? = { SessionStore.currentUser()?.xh },
    private val cached: (String) -> GpaRankInfo? = { AHUCache.getGpaRankInfo(it) },
    private val load: suspend (String) -> AhuResult<GpaRankInfo> = { AHURepository.getGpaRankInfo(it) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_gpa_rank",
        title = "查询绩点排名",
        description = "查询全程平均绩点、专业排名、总学分，以及各学期绩点与专业排名。",
        parameters = emptySchema(),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "教务系统",
        openRoute = "grade",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val studentId = currentStudentId()?.takeIf { it.isNotBlank() } ?: return notLoggedIn(spec)
        val rank = cached(studentId) ?: when (val result = load(studentId)) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val lines = buildList {
            rank.gpaSemesterSubs.forEach { semester ->
                add(
                    "学期 ${semester.semesterId} · 绩点 ${"%.2f".format(semester.gpa)}" +
                        " · 排名 ${semester.majorRank} · ${formatNumber(semester.totalCredits)} 学分"
                )
            }
            rank.updatedDateTimeStr.takeIf { it.isNotBlank() }?.let { add("数据更新于 $it") }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "全程平均绩点 ${"%.2f".format(rank.gpa)}，" +
                    "专业排名 ${rank.majorRank}/${rank.majorHeadCount}",
                lines = lines,
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "gpa",
                    headline = "%.2f".format(rank.gpa),
                    headlineSuffix = "绩点",
                    stats = listOf(
                        AssistantCardStat("专业排名", "${rank.majorRank}/${rank.majorHeadCount}"),
                        AssistantCardStat("总学分", formatNumber(rank.totalCredits)),
                    ),
                    items = rank.gpaSemesterSubs.map { semester ->
                        AssistantCardItem(
                            title = "学期 ${semester.semesterId}",
                            subtitle = "${formatNumber(semester.totalCredits)} 学分",
                            value = "绩点 ${"%.2f".format(semester.gpa)}",
                            status = "排名 ${semester.majorRank}",
                        )
                    },
                    note = rank.updatedDateTimeStr.takeIf { it.isNotBlank() }?.let { "数据更新于 $it" },
                ),
            )
        )
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}

class GetExamsCapability(
    private val student: () -> Pair<String, String>? = {
        SessionStore.currentUser()?.let { user ->
            (user.xh.orEmpty() to user.name.orEmpty()).takeIf { user.xh.isNullOrBlank().not() }
        }
    },
    private val load: suspend (String, String) -> AhuResult<List<com.ahu.ahutong.data.model.Exam>> =
        { id, name -> AHURepository.getExamInfo(isRefresh = false, studentID = id, studentName = name) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_exams",
        title = "查询考试安排",
        description = "查询教务系统里的期末考试安排：课程、时间、地点与座位号。",
        parameters = emptySchema(),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "教务系统",
        openRoute = "exam",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val (studentId, studentName) = student() ?: return notLoggedIn(spec)
        return when (val result = load(studentId, studentName)) {
            is AhuResult.Failure -> result.error.asAssistantFailure()
            is AhuResult.Success -> {
                val exams = result.value
                if (exams.isEmpty()) {
                    return AssistantOutcome.Success(
                        AssistantAnswer("目前没有考试安排", route = spec.openRoute)
                    )
                }
                val upcoming = exams.filter { it.finished != true }
                val sorted = exams.sortedWith(
                    compareBy({ it.finished == true }, { it.time.orEmpty() })
                )
                val lines = sorted.take(MAX_EXAMS).map { exam ->
                    buildString {
                        append(exam.course.orEmpty())
                        append(" · ")
                        append(exam.time.orEmpty())
                        append(" · ")
                        append(exam.location.orEmpty())
                        exam.seatNum?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                            append(" 号座")
                        }
                    }
                }
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = "共 ${exams.size} 场考试，未结束 ${upcoming.size} 场",
                        lines = lines,
                        route = spec.openRoute,
                        card = AssistantCard(
                            kind = "exams",
                            headline = upcoming.size.toString(),
                            headlineSuffix = "场未结束",
                            stats = listOf(AssistantCardStat("全部考试", exams.size.toString())),
                            items = sorted.take(MAX_CARD_EXAMS).map { exam ->
                                AssistantCardItem(
                                    title = exam.course.orEmpty(),
                                    subtitle = exam.time.orEmpty(),
                                    value = exam.location.orEmpty(),
                                    status = exam.seatNum?.takeIf { it.isNotBlank() }?.let { "$it 号座" },
                                    tone = AssistantCardTone.ACCENT,
                                )
                            },
                        ),
                    )
                )
            }
        }
    }

    private companion object {
        const val MAX_EXAMS = 12
        const val MAX_CARD_EXAMS = 20
    }
}

class GetProgramCompletionCapability(
    private val cached: () -> Pair<com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion, Long>? =
        { ProgramCompletionGateway.loadCached() },
    private val refresh: suspend () -> AhuResult<Pair<com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion, Long>> =
        { ProgramCompletionGateway.refresh() },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_program_completion",
        title = "查询培养方案完成度",
        description = "查询本科培养方案的毕业学分要求、已修学分、各模块完成情况与未通过课程。",
        parameters = emptySchema(),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "教务系统",
        openRoute = "program_completion",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val (program, _) = cached() ?: when (val result = refresh()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val unpassed = program.modules.flatMap { module ->
            module.courses.filter { it.status != "PASSED" }
        }.take(MAX_UNPASSED)
        val lines = buildList {
            program.modules.take(MAX_MODULES).forEach { module ->
                add(
                    "${module.nameZh} · 已修 ${formatCredit(module.passedCredits)}/" +
                        "${formatCredit(module.requiredCredits)} 学分"
                )
            }
            if (unpassed.isNotEmpty()) {
                add("未通过课程：" + unpassed.joinToString("、") { it.nameZh })
            }
            if (!program.auditPublished) {
                add("培养方案审核结果尚未发布，以教务系统为准")
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "培养方案完成 ${program.progressPercent}，已修 " +
                    "${formatCredit(program.passedCredits)}/${formatCredit(program.requiredCredits)} 学分",
                lines = lines,
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "completion",
                    headline = program.progressPercent,
                    subtitle = program.programName,
                    stats = listOf(
                        AssistantCardStat("已修", formatCredit(program.passedCredits)),
                        AssistantCardStat("要求", formatCredit(program.requiredCredits)),
                        AssistantCardStat("在修", formatCredit(program.takingCredits)),
                        AssistantCardStat("未通过", formatCredit(program.failedCredits)),
                    ),
                    items = program.modules.take(MAX_MODULES).map { module ->
                        AssistantCardItem(
                            title = module.nameZh,
                            subtitle = "已修 ${formatCredit(module.passedCredits)} / " +
                                "${formatCredit(module.requiredCredits)} 学分",
                            value = module.result,
                            tone = if (module.result == "PASSED") {
                                AssistantCardTone.SUCCESS
                            } else {
                                AssistantCardTone.NEUTRAL
                            },
                        )
                    },
                    note = buildString {
                        if (unpassed.isNotEmpty()) {
                            append("未通过：")
                            append(unpassed.joinToString("、") { it.nameZh })
                        }
                        if (!program.auditPublished) {
                            if (isNotEmpty()) append("\n")
                            append("审核结果尚未发布，以教务系统为准")
                        }
                    }.takeIf { it.isNotBlank() },
                ),
            )
        )
    }

    private fun formatCredit(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    private companion object {
        const val MAX_MODULES = 8
        const val MAX_UNPASSED = 10
    }
}

class GetEvaluationTasksCapability(
    private val semesters: suspend () -> AhuResult<List<com.ahu.ahutong.data.model.EvalSemester>> =
        { EvaluationRepository.getSemesters() },
    private val tasks: suspend (String) -> AhuResult<List<com.ahu.ahutong.data.model.EvalTaskItem>> =
        { semesterId -> EvaluationRepository.getEvaluationList(semesterId, evaluated = false) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_evaluation_tasks",
        title = "查询待评任务",
        description = "查询评教待办。不传 semester 时使用最近一个学期；返回学期、问卷和待评价教师。",
        parameters = schema("semester" to stringProp("学期名称或 ID；不传用最近学期")),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "评教系统",
        openRoute = "evaluation",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val list = when (val result = semesters()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        if (list.isEmpty()) {
            return AssistantOutcome.Success(
                AssistantAnswer("没有可评教的学期", route = spec.openRoute)
            )
        }
        val keyword = arguments.stringArg("semester")
        val selected = list.firstOrNull { it.id == keyword }
            ?: list.firstOrNull {
                keyword != null && (it.nameZh.contains(keyword) || it.schoolYear.contains(keyword))
            }
            ?: list.first()
        val items = when (val result = tasks(selected.id)) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val teacherCount = items.sumOf { it.taskList.sumOf { task -> task.teachers.size } }
        val lines = items.take(MAX_TASKS).map { item ->
            buildString {
                append(item.courseName.ifBlank { item.lessonNameZh })
                append(" · ")
                append(item.taskList.size)
                append(" 份问卷")
                val names = item.taskList.flatMap { task -> task.teachers.map { it.teacherName } }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (names.isNotEmpty()) {
                    append(" · ")
                    append(names.joinToString("、"))
                }
                val days = item.taskList.mapNotNull { task -> task.days.toIntOrNull() }.minOrNull()
                if (days != null) {
                    append(" · 剩余 ")
                    append(days)
                    append(" 天")
                }
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "${selected.nameZh.ifBlank { selected.schoolYear }}：" +
                    "${items.size} 个课程任务，${teacherCount} 位教师待评",
                lines = lines,
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "evaluation",
                    headline = teacherCount.toString(),
                    headlineSuffix = "位教师待评",
                    subtitle = selected.nameZh.ifBlank { selected.schoolYear },
                    stats = listOf(AssistantCardStat("课程任务", items.size.toString())),
                    items = items.take(MAX_CARD_TASKS).map { item ->
                        val names = item.taskList.flatMap { task -> task.teachers.map { it.teacherName } }
                            .filter { it.isNotBlank() }
                            .distinct()
                        val days = item.taskList.mapNotNull { task -> task.days.toIntOrNull() }.minOrNull()
                        AssistantCardItem(
                            title = item.courseName.ifBlank { item.lessonNameZh },
                            subtitle = names.joinToString("、").takeIf { it.isNotBlank() },
                            value = "${item.taskList.size} 份问卷",
                            status = days?.let { "剩余 $it 天" },
                            tone = if (days != null && days <= 3) {
                                AssistantCardTone.DANGER
                            } else {
                                AssistantCardTone.NEUTRAL
                            },
                        )
                    },
                ),
            )
        )
    }

    private companion object {
        const val MAX_TASKS = 10
        const val MAX_CARD_TASKS = 20
    }
}

internal fun notLoggedIn(spec: AssistantCapabilitySpec): AssistantOutcome.Failure =
    AssistantOutcome.Failure("not_logged_in", "请先登录安大通账号，再查询${spec.title}")
