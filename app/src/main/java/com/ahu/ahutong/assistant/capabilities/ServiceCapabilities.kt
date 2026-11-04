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
import com.ahu.ahutong.data.attendance.KqcardAttendanceRepository
import com.ahu.ahutong.data.mail.StudentMailRepository
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.weather.WeatherApi
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException

/**
 * 服务类只读能力：考勤、学生邮箱、天气。
 * 邮箱内容属于外部输入，标记 UNTRUSTED。
 */
class GetAttendanceCapability(
    private val load: suspend (Int, Int) -> com.ahu.ahutong.data.attendance.AttendancePage =
        { status, page -> KqcardAttendanceRepository.loadPage(status = status, page = page) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_attendance",
        title = "查询考勤",
        description = "查询教务考勤记录（正常、迟到、缺勤、请假），默认返回最近一页。",
        parameters = schema("page" to intProp("页码，默认 1", minimum = 1, maximum = 50)),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "教务考勤",
        openRoute = "attendance_records",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val page = (arguments.intArg("page") ?: 1).coerceIn(1, 50)
        return try {
            val result = load(0, page)
            val lines = result.records.take(MAX_RECORDS).map { record ->
                buildString {
                    append(record.classDate)
                    append(" ")
                    append(record.startTime)
                    if (record.endTime.isNotBlank()) {
                        append("-")
                        append(record.endTime)
                    }
                    append(" · ")
                    append(record.courseName)
                    append(" · ")
                    append(record.status.label)
                    val place = (record.building + record.room).trim()
                    if (place.isNotBlank()) {
                        append(" · ")
                        append(place)
                    }
                }
            }
            AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "考勤记录共 ${result.totalCount} 条",
                    lines = lines,
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "attendance",
                        headline = result.totalCount.toString(),
                        headlineSuffix = "条",
                        items = result.records.take(MAX_CARD_RECORDS).map { record ->
                            val place = (record.building + record.room).trim()
                            AssistantCardItem(
                                title = record.courseName,
                                subtitle = "${record.classDate} ${record.startTime}-${record.endTime}" +
                                    if (place.isNotBlank()) " · $place" else "",
                                status = record.status.label,
                                tone = when (record.status.label) {
                                    "正常" -> AssistantCardTone.SUCCESS
                                    "迟到", "缺勤" -> AssistantCardTone.DANGER
                                    else -> AssistantCardTone.NEUTRAL
                                },
                            )
                        },
                    ),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure(
                "attendance_failed",
                error.message ?: "考勤查询失败，稍后再试",
                retryable = true,
            )
        }
    }

    private companion object {
        const val MAX_RECORDS = 12
        const val MAX_CARD_RECORDS = 20
    }
}

class GetMailCapability(
    private val repository: () -> StudentMailRepository = { StudentMailRepository() },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_mail",
        title = "查询学生邮箱",
        description = "查询或搜索学生邮箱。不传 message_id 时返回文件夹和邮件列表；传 message_id 时返回邮件正文与附件。" +
            "folder 可传文件夹名（如 收件箱）或 ID；query 会在主题和发件人中搜索。",
        parameters = schema(
            "folder" to stringProp("文件夹名称或 ID，默认 收件箱"),
            "query" to stringProp("搜索关键词，匹配主题或发件人"),
            "unread_only" to boolProp("只看未读，默认 false"),
            "message_id" to stringProp("邮件 ID；传入时返回正文"),
            "limit" to intProp("列表条数，默认 20，最多 30", minimum = 1, maximum = 30),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "学生邮箱",
        openRoute = "student_mail",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val mail = repository()
        return try {
            mail.connect()
            val messageId = arguments.stringArg("message_id")
            if (messageId != null) {
                detail(mail.detail(messageId))
            } else {
                list(mail, arguments)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure(
                "mail_failed",
                error.message ?: "邮箱查询失败，稍后再试",
                retryable = true,
            )
        }
    }

    private suspend fun list(mail: StudentMailRepository, arguments: JsonObject): AssistantOutcome {
        val folders = mail.folders()
        val folderKey = arguments.stringArg("folder") ?: "收件箱"
        val folder = folders.firstOrNull { it.id.toString() == folderKey }
            ?: folders.firstOrNull { it.name.contains(folderKey) }
            ?: folders.firstOrNull()
            ?: return AssistantOutcome.Failure("mail_empty", "邮箱没有可用文件夹")
        val unreadOnly = arguments.boolArg("unread_only", false)
        val page = mail.messages(folder.id, 0, unreadOnly)
        val query = arguments.stringArg("query")
        val limit = (arguments.intArg("limit") ?: 20).coerceIn(1, 30)
        val messages = page.messages
            .filter { message ->
                query == null ||
                    message.subject.contains(query, ignoreCase = true) ||
                    message.from.contains(query, ignoreCase = true)
            }
            .take(limit)
        val lines = messages.map { message ->
            buildString {
                append(message.from)
                append(" · ")
                append(message.subject.ifBlank { "(无主题)" })
                if (message.date.isNotBlank()) {
                    append(" · ")
                    append(message.date)
                }
                if (!message.read) append(" · 未读")
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "${folder.name}共 ${page.total} 封，显示 ${messages.size} 封",
                lines = lines,
                route = spec.openRoute,
                trust = AssistantTrust.UNTRUSTED,
                card = AssistantCard(
                    kind = "mail",
                    headline = page.total.toString(),
                    headlineSuffix = "封",
                    subtitle = folder.name,
                    items = messages.map { message ->
                        AssistantCardItem(
                            title = message.subject.ifBlank { "(无主题)" },
                            subtitle = message.from,
                            value = message.date.takeIf { it.isNotBlank() },
                            status = if (message.read) null else "未读",
                            tone = if (message.read) {
                                AssistantCardTone.NEUTRAL
                            } else {
                                AssistantCardTone.DANGER
                            },
                        )
                    },
                ),
            )
        )
    }

    private fun detail(detail: com.ahu.ahutong.data.mail.MailDetail): AssistantOutcome {
        val body = detail.text.ifBlank { detail.html }
            .replace(HTML_TAG, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_BODY_CHARS)
        val lines = buildList {
            if (detail.from.isNotEmpty()) add("发件人：" + detail.from.joinToString(", "))
            if (detail.cc.isNotEmpty()) add("抄送：" + detail.cc.joinToString(", "))
            if (detail.attachments.isNotEmpty()) {
                add("附件：" + detail.attachments.joinToString("、") { it.name })
            }
            if (body.isNotBlank()) add(body)
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = detail.subject.ifBlank { "(无主题)" },
                lines = lines,
                route = spec.openRoute,
                trust = AssistantTrust.UNTRUSTED,
                card = AssistantCard(
                    kind = "mail_detail",
                    headline = detail.subject.ifBlank { "(无主题)" },
                    subtitle = detail.from.joinToString(", ").takeIf { it.isNotBlank() },
                    stats = listOf(
                        AssistantCardStat(
                            "附件",
                            detail.attachments.size.toString(),
                        ),
                    ),
                    items = detail.attachments.map { attachment ->
                        AssistantCardItem(
                            title = attachment.name,
                            subtitle = "${attachment.size} B",
                        )
                    },
                    note = body.takeIf { it.isNotBlank() },
                ),
            )
        )
    }

    private companion object {
        const val MAX_BODY_CHARS = 800
        val HTML_TAG = Regex("<[^>]+>")
        val WHITESPACE = Regex("\\s+")
    }
}

class GetWeatherCapability(
    private val load: suspend (String) -> com.ahu.ahutong.data.weather.WeatherResponse =
        { city -> WeatherApi.API.getWeather(city = city) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_weather",
        title = "查询天气",
        description = "查询合肥（安徽大学磬苑校区所在地）的实时天气与未来预报。",
        parameters = schema("city" to stringProp("城市名，默认 合肥")),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "天气服务",
        openRoute = "weather",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val city = arguments.stringArg("city") ?: "合肥"
        return try {
            val weather = load(city)
            val lines = buildList {
                weather.feelsLike?.let { add("体感 ${trimNumber(it)}°") }
                val humidity = weather.humidity
                if (humidity != null) add("湿度 $humidity%")
                val wind = listOfNotNull(weather.windDirection, weather.windPower)
                    .joinToString(" ")
                    .trim()
                if (wind.isNotBlank()) add("风：$wind")
                weather.aqi?.let { aqi ->
                    add("AQI $aqi" + (weather.aqiCategory?.let { "（$it）" } ?: ""))
                }
                if (weather.tempMin != null && weather.tempMax != null) {
                    add("今日 ${trimNumber(weather.tempMin)}~${trimNumber(weather.tempMax)}°")
                }
                weather.reportTime?.takeIf { it.isNotBlank() }?.let { add("更新于 $it") }
            }
            AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "${weather.city ?: city} ${weather.weather.orEmpty()} " +
                        "${weather.temperature?.let { trimNumber(it) } ?: "-"}°C",
                    lines = lines,
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "weather",
                        headline = weather.temperature?.let { trimNumber(it) } ?: "-",
                        headlineSuffix = "°",
                        subtitle = listOfNotNull(weather.city, weather.weather)
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() },
                        stats = listOfNotNull(
                            weather.feelsLike?.let {
                                AssistantCardStat("体感", "${trimNumber(it)}°")
                            },
                            weather.humidity?.let { AssistantCardStat("湿度", "$it%") },
                            weather.aqi?.let { AssistantCardStat("AQI", it.toString()) },
                            if (weather.tempMin != null && weather.tempMax != null) {
                                AssistantCardStat(
                                    "今日",
                                    "${trimNumber(weather.tempMin)}~${trimNumber(weather.tempMax)}°",
                                )
                            } else null,
                        ),
                        note = weather.reportTime?.takeIf { it.isNotBlank() }?.let { "更新于 $it" },
                    ),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure(
                "weather_failed",
                error.message ?: "天气查询失败，稍后再试",
                retryable = true,
            )
        }
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
