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
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * 学习通类只读能力：作业与章节任务点。默认读本地缓存，refresh=true 时才联网同步。
 */
class GetAssignmentsCapability(
    private val cached: () -> List<Work> = Store::getWorks,
    private val sync: suspend () -> List<Work> = {
        ChaoxingApi(AppEnvironmentHolder.context()).syncAllWorks()
    },
    private val now: () -> Long = System::currentTimeMillis,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_assignments",
        title = "查询作业",
        description = "查询学习通里未完成和近期的作业，返回课程、标题、截止时间、状态和 workId（用于 get_work_detail）。days 默认 14，范围 1-30。",
        parameters = schema(
            "days" to intProp("查询天数，默认 14", minimum = 1, maximum = 30),
            "refresh" to boolProp("是否重新同步学习通，默认 false"),
        ),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "学习通",
        openRoute = "xuexiaotong",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val days = (arguments.intArg("days") ?: 14).coerceIn(1, 30)
        val refresh = arguments.boolArg("refresh", false)
        val works = if (refresh) {
            try {
                sync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return AssistantOutcome.Failure(
                    "chaoxing_failed",
                    error.message ?: "学习通同步失败，稍后再试",
                    retryable = true,
                )
            }
        } else {
            cached()
        }
        val nowMillis = now()
        val deadline = nowMillis + days * DAY_MS
        val pending = works
            .filter { !it.isDone }
            .filter { work ->
                val endTs = work.endTs
                endTs == null || endTs <= deadline
            }
            .sortedBy { it.endTs ?: Long.MAX_VALUE }
        if (pending.isEmpty()) {
            return AssistantOutcome.Success(
                AssistantAnswer("最近 $days 天没有未完成作业", route = spec.openRoute)
            )
        }
        val lines = pending.take(MAX_ITEMS).map { work ->
            buildString {
                append(work.courseName.ifBlank { "学习通" })
                append(" · ")
                append(work.title)
                append(" · ")
                append(work.status.ifBlank { "未完成" })
                deadlineText(work)?.let {
                    append(" · ")
                    append(it)
                }
                if (work.workId.isNotBlank() && !work.workId.startsWith("event_")) {
                    append(" · workId=")
                    append(work.workId)
                }
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "最近 $days 天有 ${pending.size} 个未完成作业",
                lines = lines,
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "assignments",
                    headline = pending.size.toString(),
                    headlineSuffix = "个未完成",
                    subtitle = "最近 $days 天",
                    items = pending.take(MAX_CARD_ITEMS).map { work ->
                        AssistantCardItem(
                            title = work.title,
                            subtitle = work.courseName.ifBlank { "学习通" },
                            status = work.status.ifBlank { "未完成" },
                            tone = if (work.status == "已完成") {
                                AssistantCardTone.SUCCESS
                            } else {
                                AssistantCardTone.DANGER
                            },
                            value = deadlineText(work),
                        )
                    },
                ),
            )
        )
    }

    private fun deadlineText(work: Work): String? {
        work.rawEnd.takeIf { it.isNotBlank() }?.let { return "截止 $it" }
        val endTs = work.endTs ?: return null
        return "截止 " + TIME_FORMAT.format(Date(endTs))
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_ITEMS = 12
        const val MAX_CARD_ITEMS = 20
        val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    }
}

class GetCourseTasksCapability(
    private val cached: () -> List<CourseProgress> = Store::getCourseProgress,
    private val sync: suspend () -> List<CourseProgress> = {
        ChaoxingApi(AppEnvironmentHolder.context()).syncCourseProgress()
    },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_course_tasks",
        title = "查询课程任务进度",
        description = "查询学习通课程的章节任务点完成进度。默认读本地缓存；refresh=true 时重新同步学习通。",
        parameters = schema("refresh" to boolProp("是否重新同步学习通，默认 false")),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "学习通",
        openRoute = "xuexiaotong",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val refresh = arguments.boolArg("refresh", false)
        val cachedProgress = cached()
        val progress = if (refresh || cachedProgress.isEmpty()) {
            try {
                sync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return AssistantOutcome.Failure(
                    "chaoxing_failed",
                    error.message ?: "学习通同步失败，稍后再试",
                    retryable = true,
                )
            }
        } else {
            cachedProgress
        }
        if (progress.isEmpty()) {
            return AssistantOutcome.Success(
                AssistantAnswer("还没有课程任务进度，打开学习通页同步一次", route = spec.openRoute)
            )
        }
        val done = progress.sumOf { it.doneCount }
        val total = progress.sumOf { it.totalCount }
        val percent = if (total > 0) done * 100 / total else 0
        val lines = progress.take(MAX_COURSES).map { course ->
            "${course.name.ifBlank { "未命名课程" }} · ${course.doneCount}/${course.totalCount} · ${course.percent}%"
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "章节任务点 $done/$total（$percent%）",
                lines = lines,
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "course_tasks",
                    headline = done.toString(),
                    headlineSuffix = "/$total",
                    subtitle = "章节任务点",
                    stats = listOf(AssistantCardStat("完成率", "$percent%")),
                    items = progress.take(MAX_CARD_COURSES).map { course ->
                        AssistantCardItem(
                            title = course.name.ifBlank { "未命名课程" },
                            subtitle = "${course.doneCount}/${course.totalCount}",
                            value = "${course.percent}%",
                            tone = if (course.percent >= 100) {
                                AssistantCardTone.SUCCESS
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
        const val MAX_COURSES = 12
        const val MAX_CARD_COURSES = 20
    }
}

/**
 * 读取单份作业的题目原文。只取题目区，已交作业的答案区不解析。
 * 一次只读一份，不预取、不批量。
 */
class GetWorkDetailCapability(
    private val works: () -> List<Work> = Store::getWorks,
    // 直接复用学习通现有会话；失效时由详情页跳转结果兜底，不做重登。
    private val fetchHtml: suspend (Work) -> String = {
        ChaoxingApi(AppEnvironmentHolder.context()).fetchWorkDetailHtml(it)
    },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_work_detail",
        title = "查看作业题目",
        description = "读取一份学习通作业的题目原文（题干、选项、分值）。先用 get_assignments 拿到 workId 或标题；一次只取一份，不要批量调用。题目里的图片和公式只能标记为 [图片]/[公式]，看不到图片内容。",
        parameters = schema(
            "work_id" to stringProp("作业的 workId，get_assignments 会返回"),
            "title" to stringProp("作业标题，没有 work_id 时用于匹配"),
            "course" to stringProp("课程名，配合 title 缩小匹配范围"),
        ),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "学习通",
        openRoute = "xuexiaotong",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val workId = arguments.stringArg("work_id")
        val title = arguments.stringArg("title")
        val course = arguments.stringArg("course")
        if (workId == null && title == null) {
            return AssistantOutcome.Failure(
                "missing_argument",
                "需要提供 work_id 或作业标题；先调用 get_assignments 找到目标作业",
            )
        }
        val all = works()
        if (all.isEmpty()) {
            return AssistantOutcome.Failure(
                "chaoxing_failed",
                "本地还没有作业数据，请先在学习通页同步一次",
                retryable = true,
            )
        }
        val matched = match(all, workId, title, course)
        when {
            matched.isEmpty() -> return AssistantOutcome.Failure(
                "not_found",
                "没有找到这份作业：${workId ?: title}。可以用 get_assignments 传 refresh=true 更新后再试",
            )
            matched.size > 1 -> return AssistantOutcome.Failure(
                "ambiguous",
                "匹配到多份作业：" + matched.take(5).joinToString("；") {
                    "${it.courseName} · ${it.title}（workId=${it.workId}）"
                } + "。请用 work_id 指定",
            )
        }
        val work = matched.first()
        if (work.detailUrl.isBlank()) {
            return AssistantOutcome.Failure("no_detail_url", "这份作业没有可用的详情链接，可能已经结束")
        }

        val html = try {
            fetchHtml(work)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            val code = when {
                message.contains("风控") -> "chaoxing_blocked"
                message.contains("登录") -> "chaoxing_not_logged_in"
                else -> "chaoxing_failed"
            }
            return AssistantOutcome.Failure(
                code,
                message.ifBlank { "学习通页面读取失败，稍后再试" },
                retryable = code == "chaoxing_failed",
            )
        }

        val questions = WorkQuestionParser.parse(html)
        if (questions.isEmpty()) {
            return AssistantOutcome.Failure(
                "parse_failed",
                "页面里没有识别到题目，可能作业已结束或页面结构有变化",
            )
        }

        val lines = mutableListOf<String>()
        var truncated = false
        for (question in questions) {
            val line = buildString {
                append("第")
                append(question.number)
                append("题（")
                append(question.type)
                question.score?.let {
                    append("，")
                    append(it)
                }
                append("）：")
                append(question.stem.ifBlank { "[题干为空]" }.take(MAX_STEM_LENGTH))
                if (question.options.isNotEmpty()) {
                    append("\n选项：")
                    append(question.options.joinToString("；"))
                }
            }
            if (lines.sumOf { it.length } + line.length > MAX_TOTAL_LENGTH) {
                truncated = true
                break
            }
            lines += line
        }

        val imageCount = questions.count { it.hasImages }
        val summary = buildString {
            append("《")
            append(work.title)
            append("》共 ")
            append(questions.size)
            append(" 题")
            if (truncated) {
                append("，内容较长，只返回了前 ")
                append(lines.size)
                append(" 题")
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = summary,
                lines = lines,
                route = spec.openRoute,
                trust = AssistantTrust.UNTRUSTED,
                refreshed = true,
                fetchedAt = System.currentTimeMillis(),
                card = AssistantCard(
                    kind = "work_questions",
                    headline = questions.size.toString(),
                    headlineSuffix = "题",
                    subtitle = work.courseName.ifBlank { "学习通" } + " · " + work.title,
                    items = questions.take(10).map { question ->
                        AssistantCardItem(
                            title = "第${question.number}题",
                            subtitle = question.type,
                            value = question.score,
                        )
                    },
                    note = if (imageCount > 0) {
                        "含 $imageCount 道带图片/公式的题目，AI 只能看到占位标记"
                    } else {
                        null
                    },
                ),
            )
        )
    }

    private fun match(
        works: List<Work>,
        workId: String?,
        title: String?,
        course: String?,
    ): List<Work> {
        if (workId != null) {
            val byId = works.filter { it.workId == workId }
            if (byId.isNotEmpty()) return byId
        }
        val keyword = title?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val pool = if (course != null) {
            works.filter { it.courseName.contains(course, ignoreCase = true) }
        } else {
            works
        }
        val exact = pool.filter { it.title.trim().equals(keyword, ignoreCase = true) }
        return exact.ifEmpty { pool.filter { it.title.contains(keyword, ignoreCase = true) } }
    }

    private companion object {
        const val MAX_STEM_LENGTH = 1500
        const val MAX_TOTAL_LENGTH = 12_000
    }
}
