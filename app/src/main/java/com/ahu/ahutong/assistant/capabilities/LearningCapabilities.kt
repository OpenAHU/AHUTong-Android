package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
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
        description = "查询学习通里未完成和近期的作业，返回课程、标题、截止时间与状态。days 默认 14，范围 1-30。",
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
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "最近 $days 天有 ${pending.size} 个未完成作业",
                lines = lines,
                route = spec.openRoute,
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
            )
        )
    }

    private companion object {
        const val MAX_COURSES = 12
    }
}
