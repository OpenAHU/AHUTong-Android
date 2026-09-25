package com.ahu.ahutong.data

import com.ahu.ahutong.core.common.toUserMessage
import android.net.Uri
import android.util.Log
import com.ahu.ahutong.data.crawler.api.jwxt.EvaluationApi
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.EvalApiResponse
import com.ahu.ahutong.data.model.EvalCheckParam
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalQuestionnaire
import com.ahu.ahutong.data.model.EvalSearchResult
import com.ahu.ahutong.data.model.EvalSemester
import com.ahu.ahutong.data.model.EvalSubmitRequest
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.EvalTaskItem
import com.ahu.ahutong.data.model.EvalTeacher
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException
import java.net.URLDecoder
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.DefaultAhuSession
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import kotlinx.coroutines.CancellationException

object EvaluationRepository {

    private const val TAG = "EvaluationRepository"
    private const val EVALUATION_SERVICE_URL =
        "https://jw.ahu.edu.cn/eams5-evaluation-service/"

    private val api by lazy { EvaluationApi.API }
    private val gson = Gson()

    @Volatile
    private var token: String = ""

    @Volatile
    private var currentSemesterId: String = ""

    private val sessionGate = EvaluationSessionGate()

    suspend fun getSemesters(): AhuResult<List<EvalSemester>> = evalResult {
        requestWithSession { api.getSemesters() }.requireData().orEmpty().map { semester ->
            semester.copy(
                id = semester.id.orEmpty(),
                nameZh = semester.nameZh.orEmpty(),
                nameEn = semester.nameEn.orEmpty(),
                code = semester.code.orEmpty(),
                schoolYear = semester.schoolYear.orEmpty()
            )
        }
    }

    fun getCurrentSemesterId(): String = currentSemesterId

    /** 登出或切换账号时同时清掉内存、请求头、持久副本与评教域 Cookie。 */
    suspend fun clearSession() {
        sessionGate.withSession {
            clearSessionState("session cleared")
        }
    }

    private fun clearSessionState(reason: String) {
        token = ""
        currentSemesterId = ""
        EvaluationApi.setAuthorizationToken("")
        runCatching { SessionStore.saveEvalToken("") }
            .onFailure { Log.w(TAG, "failed to clear persisted evaluation token", it) }
        runCatching { clearEvaluationServiceCookies(reason) }
            .onFailure { Log.w(TAG, "failed to clear evaluation cookies", it) }
    }

    suspend fun getEvaluationList(
        semesterId: String,
        evaluated: Boolean = false,
        page: Int = 1,
        pageSize: Int = 50
    ): AhuResult<List<EvalTaskItem>> = evalResult {
        requestWithSession {
            api.getEvaluationList(
                page = "$page,$pageSize",
                semesterId = semesterId,
                evaluated = evaluated
            )
        }.requireData().items.orEmpty().map(EvalTaskItem::sanitized)
    }

    suspend fun getQuestions(questionnaireId: String): AhuResult<EvalQuestionnaireForm> = evalResult {
        val questionnaire = requestWithSession {
            api.getQuestionnaire(questionnaireId)
        }.requireData()
        val type = object : TypeToken<List<EvalQuestion>>() {}.type
        val sanitizedQuestionnaire = questionnaire.copy(
            id = questionnaire.id.orEmpty(),
            nameZh = questionnaire.nameZh.orEmpty(),
            questions = questionnaire.questions.orEmpty().ifBlank { "[]" },
            questionNum = questionnaire.questionNum.orEmpty(),
            evaluateTypeId = questionnaire.evaluateTypeId.orEmpty(),
            name = questionnaire.name.orEmpty()
        )
        val questions = gson.fromJson<List<EvalQuestion>>(
            sanitizedQuestionnaire.questions,
            type
        ).orEmpty()
        EvalQuestionnaireForm(sanitizedQuestionnaire, questions)
    }

    suspend fun checkParam(stdSumTaskId: String): AhuResult<EvalCheckParam> = evalResult {
        requestWithSession { api.checkParam(stdSumTaskId) }.requireData()
    }

    suspend fun checkSubmit(request: EvalSubmitRequest): AhuResult<String> = evalResult {
        val response = requestWithSession { api.checkSubmit(request) }
        check(response.code == 0) { response.msg.orEmpty().ifBlank { "提交检查失败" } }
        response.data.orEmpty()
    }

    suspend fun submit(request: EvalSubmitRequest): AhuResult<Unit> = evalResult {
        val response = requestWithSession { api.submit(request) }
        check(response.code == 0) { response.msg.orEmpty().ifBlank { "提交失败" } }
    }

    private suspend fun <T> requestWithSession(
        block: suspend () -> EvalApiResponse<T>
    ): EvalApiResponse<T> = sessionGate.withSession {
        // ponytail: one serialized evaluation session; move tokens onto each request if parallel
        // evaluation calls ever become a measured requirement.
        ensureToken(forceRefresh = false)
        val first = try {
            block()
        } catch (e: HttpException) {
            if (e.code() != 401) {
                throw IllegalStateException("评教接口请求失败（HTTP ${e.code()}）", e)
            }
            ensureToken(forceRefresh = true)
            return@withSession callEvaluationApi("评教接口请求失败") { block() }
        }
        if (first.code == 0) return@withSession first

        // Business validation errors are final responses, not evidence of an expired login.
        // Retrying every non-zero response forced a complete token bootstrap and made the
        // evaluation page look broken or extremely slow.
        if (!first.indicatesExpiredSession()) return@withSession first

        ensureToken(forceRefresh = true)
        callEvaluationApi("评教接口请求失败") { block() }
    }

    private suspend fun ensureToken(forceRefresh: Boolean): String {
        check(AHUCache.canUseUndergraduateAcademics()) { "研究生账号不支持本科评教" }
        if (!forceRefresh && token.isNotBlank()) return token

        if (!forceRefresh) {
            val cached = SessionStore.evalToken().orEmpty()
            if (cached.isNotBlank()) {
                val renewed = renewToken(cached)
                renewed.getOrNull()?.let { return it }
                Log.i(TAG, "cached eval token is not reusable (details suppressed)")
                SessionStore.saveEvalToken("")
            }
        }

        val entryToken = fetchEntryToken()
        clearEvaluationServiceCookies("before token renew")
        return renewToken(entryToken).getOrThrow()
    }

    private suspend fun renewToken(seedToken: String): Result<String> = runCatching {
        EvaluationApi.setAuthorizationToken(seedToken)
        val response = callEvaluationApi("评教 token 续期失败") {
            api.tokenRenew(mapOf("token" to seedToken))
        }
        check(response.code == 0 && response.data?.token?.isNotBlank() == true) {
            response.msg.orEmpty().ifBlank { "评教 token 续期失败" }
        }
        val renewed = response.data!!.token
        EvaluationApi.setAuthorizationToken(renewed)
        Log.i(TAG, "eval authorization token updated")
        Log.i(TAG, "eval token renewed")
        val account = callEvaluationApi("评教身份初始化失败") {
            api.getAccount(renewed)
        }
        check(account.code == 0) { account.msg.orEmpty().ifBlank { "评教身份初始化失败" } }
        val renewedSemesterId = account.data?.currentSemesterId.orEmpty()
        val identity = account.data?.currentIdentity
            ?.takeIf { it.isNotBlank() }
            ?: "STUDENT"
        Log.i(TAG, "eval account initialized: identity=$identity")
        val currentYear = callEvaluationApi("评教学年初始化失败") {
            api.getCurrentYear(renewed)
        }
        check(currentYear.code == 0) { currentYear.msg.orEmpty().ifBlank { "评教学年初始化失败" } }
        Log.i(TAG, "eval current year initialized")
        val menu = getHomeMenuWithCookieRetry(identity)
        check(menu.code == 0) { menu.msg.orEmpty().ifBlank { "评教菜单初始化失败" } }
        Log.i(TAG, "eval menu initialized")
        SessionStore.saveEvalToken(renewed)
        currentSemesterId = renewedSemesterId
        token = renewed
        EvaluationApi.setAuthorizationToken(renewed)
        renewed
    }

    private suspend fun getHomeMenuWithCookieRetry(identity: String): EvalApiResponse<JsonElement> {
        return try {
            api.getHomeMenu(identity)
        } catch (e: HttpException) {
            if (e.code() != 401) {
                throw IllegalStateException("评教菜单初始化失败（HTTP ${e.code()}）", e)
            }
            clearEvaluationServiceCookies("home menu 401")
            callEvaluationApi("评教菜单初始化失败") {
                api.getHomeMenu(identity)
            }
        }
    }

    private suspend fun fetchEntryToken(): String {
        val first = requestEntryToken()
        first.token?.let { return it }

        if (first.shouldRefreshJwxtSession) {
            refreshJwxtSession(first.message)
            val second = requestEntryToken()
            second.token?.let { return it }
            throw IllegalStateException(second.message)
        }

        throw IllegalStateException(first.message)
    }

    private suspend fun requestEntryToken(): EntryTokenResult {
        val response = api.enterEvaluationSystem()
        response.body()?.close()
        response.errorBody()?.close()
        val location = response.headers()["Location"].orEmpty()
        val token = extractToken(location)
        val redactedLocation = redactLocation(location)
        Log.i(
            TAG,
            "entry status=${response.code()} location=${redactedLocation.ifBlank { "<empty>" }}"
        )
        if (token != null) {
            return EntryTokenResult(token = token)
        }

        val shouldRefresh = response.code() == 401 || isLoginRedirect(location)
        val message = when {
            location.isBlank() -> {
                "无法进入评教系统（HTTP ${response.code()}），请先确认教务系统登录状态"
            }
            shouldRefresh -> {
                "教务登录态已失效，评教入口跳回登录页（HTTP ${response.code()}）"
            }
            else -> {
                "评教入口未返回 token（HTTP ${response.code()}，Location=$redactedLocation）"
            }
        }
        return EntryTokenResult(
            token = null,
            shouldRefreshJwxtSession = shouldRefresh,
            message = message
        )
    }

    private suspend fun refreshJwxtSession(reason: String) {
        Log.i(TAG, "refresh jwxt session for evaluation: $reason")
        val observedGeneration = SessionRefreshCoordinator.currentGeneration()
        clearSessionState("before jwxt session refresh")
        check(DefaultAhuSession.ensureFresh(observedGeneration)) {
            "教务静默登录失败，请重新登录后再试"
        }
        Log.i(TAG, "jwxt session refreshed for evaluation")
    }

    private fun clearEvaluationServiceCookies(reason: String) {
        Log.i(TAG, "clear evaluation service cookies: $reason")
        CookieManager.cookieJar.clearCookiesForUrl(EVALUATION_SERVICE_URL)
    }

    private fun extractToken(location: String): String? {
        val fragment = Uri.parse(location).encodedFragment.orEmpty()
        val query = fragment.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        val token = query.split('&')
            .firstOrNull { it.substringBefore('=') == "token" }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return URLDecoder.decode(token, Charsets.UTF_8.name())
    }

    private fun isLoginRedirect(location: String): Boolean {
        if (location.isBlank()) return false
        return location.contains("tologin", ignoreCase = true) ||
            location.contains("refer=", ignoreCase = true) ||
            location.contains("/student/login", ignoreCase = true) ||
            location.contains("/cas/login", ignoreCase = true)
    }

    private fun redactLocation(location: String): String {
        return location
            .replace(Regex("token=[^&#]+"), "token=<redacted>")
            .take(300)
    }

    /**
     * 统一出口：内部仍以异常表达失败（`check` / `requireData` / 网络异常），但**不跨模块边界**——
     * 对外只有 [AhuResult]（ADR 0001 规则 1），分类走唯一的 `toAhuError()`。
     *
     * 比 `runCatching` 多做一件事：取消原样抛出。否则界面已经关闭，请求仍会继续跑完并把错误提示写回去。
     */
    private inline fun <T> evalResult(block: () -> T): AhuResult<T> = try {
        AhuResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AhuResult.Failure(e.toAhuError())
    }

    private fun <T> EvalApiResponse<T>.requireData(): T {
        // EvalApiResponse 已迁到 :core:model，跨模块属性无法智能转换，先取局部变量。
        val payload = data
        check(code == 0 && payload != null) { msg.orEmpty().ifBlank { "评教接口返回异常" } }
        return payload
    }

    private fun EvalApiResponse<*>.indicatesExpiredSession(): Boolean {
        val normalized = msg.orEmpty().lowercase()
        return code == 401 ||
            normalized.contains("token") ||
            normalized.contains("unauthorized") ||
            normalized.contains("未登录") ||
            normalized.contains("登录失效") ||
            normalized.contains("登录过期")
    }

    private suspend fun <T> callEvaluationApi(
        stage: String,
        block: suspend () -> EvalApiResponse<T>
    ): EvalApiResponse<T> = try {
        block()
    } catch (e: HttpException) {
        throw IllegalStateException("$stage（HTTP ${e.code()}）", e)
    }

    private data class EntryTokenResult(
        val token: String?,
        val shouldRefreshJwxtSession: Boolean = false,
        val message: String = ""
    )
}

/** Gson can still assign JSON null to Kotlin non-null properties; normalize at the API edge. */
private fun EvalTaskItem.sanitized(): EvalTaskItem = copy(
    lessonId = lessonId.orEmpty(),
    studentId = studentId.orEmpty(),
    courseName = courseName.orEmpty(),
    lessonCode = lessonCode.orEmpty(),
    lessonNameZh = lessonNameZh.orEmpty(),
    taskList = taskList.orEmpty().map(EvalTask::sanitized)
)

private fun EvalTask.sanitized(): EvalTask = copy(
    stdSumEvaBatchId = stdSumEvaBatchId.orEmpty(),
    evaluationQuestionnaireId = evaluationQuestionnaireId.orEmpty(),
    evaluationQuestionnaireName = evaluationQuestionnaireName.orEmpty(),
    teachers = teachers.orEmpty().map(EvalTeacher::sanitized),
    days = days.orEmpty(),
    stdSumTaskId = stdSumTaskId.orEmpty()
)

private fun EvalTeacher.sanitized(): EvalTeacher = copy(
    stdSumTaskId = stdSumTaskId.orEmpty(),
    teacherId = teacherId.orEmpty(),
    personId = personId.orEmpty(),
    role = role.orEmpty(),
    teacherName = teacherName.orEmpty(),
    status = status.orEmpty(),
    code = code.orEmpty()
)

data class EvalQuestionnaireForm(
    val questionnaire: EvalQuestionnaire,
    val questions: List<EvalQuestion>
)
