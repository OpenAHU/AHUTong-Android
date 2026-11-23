package com.ahu.ahutong.data

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.CompletionCourse
import com.ahu.ahutong.data.crawler.model.jwxt.CompletionModule
import com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion
import com.ahu.ahutong.data.crawler.utils.ProgramCompletionHtmlParser
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.network.AppStatusReporting
import com.ahu.ahutong.data.network.StatusEndpoint
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 培养方案完成情况的取数网关（调研报告 §4.1：界面不直接碰协议 API）。
 *
 * - stdId 复用成绩流程的档案解析（同一 id 空间）：AHUCache 缓存 → AHURepository 档案接口
 *   → 入口页 302 兜底；多档案（辅修/微专业）v1 取主修档案。
 * - 缓存：映射后的领域模型 JSON（**不含 model.student，PII 不落盘**）+ fetchedAt，
 *   per-user 分箱（AHUCache userPutString 体系，换号自动隔离）。
 * - 会话：401/302 由 JwxtApi 拦截器链自动续期；解析层检测到登录页 HTML 归 Unauthorized，
 *   结构不符归 ProtocolChanged（校方改版哨兵），不当网络错误。
 * - 刷新策略：低频数据（一学期变几次），本地缓存优先 + 手动刷新，不做自动轮询。
 */
object ProgramCompletionGateway {

    private const val TAG = "ProgramCompletionGateway"
    private val gson = Gson()

    private data class CacheEnvelope(val fetchedAt: Long, val data: ProgramCompletion)

    /** 本地缓存（含缓存时间），无缓存返回 null。 */
    fun loadCached(): Pair<ProgramCompletion, Long>? {
        val stdId = AHUCache.getJwxtStudentId() ?: return null
        val raw = AHUCache.getProgramCompletionJson(stdId) ?: return null
        return runCatching {
            val envelope = gson.fromJson(raw, CacheEnvelope::class.java)
            envelope.data to envelope.fetchedAt
        }.onFailure { Log.w(TAG, "cache corrupted, ignored", it) }.getOrNull()
    }

    /** 拉取并刷新缓存。本科门控、mock 模式、登录页检测都在这里。 */
    suspend fun refresh(): AhuResult<Pair<ProgramCompletion, Long>> = withContext(Dispatchers.IO) {
        if (!AHUCache.canUseUndergraduateAcademics()) {
            return@withContext AhuResult.Failure(AhuError.Unauthorized("研究生账号暂不支持培养方案完成情况"))
        }
        if (AHUCache.getMockData()) {
            val sample = sampleCompletion()
            return@withContext AhuResult.Success(sample to System.currentTimeMillis())
        }
        val startedAt = System.currentTimeMillis()
        val result: AhuResult<Pair<ProgramCompletion, Long>> = try {
            val stdId = resolveStudentId()
                ?: return@withContext AppStatusReporting.reportCampusResult(
                    StatusEndpoint.AHU_JW,
                    "programCompletion",
                    AhuResult.Failure(AhuError.ProtocolChanged("未能解析教务学生档案 id")),
                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                )
            val response = JwxtApi.API.getProgramCompletionInfo(stdId)
            val html = response.body()?.string()
                ?: return@withContext AppStatusReporting.reportCampusResult(
                    StatusEndpoint.AHU_JW,
                    "programCompletion",
                    AhuResult.Failure(AhuError.Server(response.code(), "教务返回空响应")),
                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                )
            if (looksLikeLoginPage(html)) {
                return@withContext AhuResult.Failure(AhuError.Unauthorized("教务会话已失效，请重新登录"))
            }
            val parsed = try {
                ProgramCompletionHtmlParser.parse(html)
            } catch (e: ProgramCompletionHtmlParser.ParseException) {
                return@withContext AppStatusReporting.reportCampusResult(
                    StatusEndpoint.AHU_JW,
                    "programCompletion",
                    AhuResult.Failure(AhuError.ProtocolChanged(e.message ?: "解析失败")),
                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                )
            }
            val fetchedAt = System.currentTimeMillis()
            AHUCache.saveProgramCompletionJson(stdId, gson.toJson(CacheEnvelope(fetchedAt, parsed)))
            AhuResult.Success(parsed to fetchedAt)
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            AhuResult.Failure(e.toAhuError())
        }
        AppStatusReporting.reportCampusResult(
            StatusEndpoint.AHU_JW,
            "programCompletion",
            result,
            (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        )
    }

    /**
     * stdId 解析（调研报告 §4.3 的优先级）：
     * AHUCache 直取 → 成绩档案接口（含缓存与多档案解析）→ 入口页 302 Location 兜底。
     */
    private suspend fun resolveStudentId(): String? {
        AHUCache.getJwxtStudentId()?.takeIf { it.isNotBlank() }?.let { return it }
        AHURepository.getGradeStudentProfiles().firstOrNull()?.id?.let { return it }
        return runCatching {
            // Retrofit 默认跟随重定向：终态 URL 末段是数字即单档案 302 成功
            val finalUrl = JwxtApi.API.fetchProgramCompletionEntry().raw().request.url.toString()
            finalUrl.trimEnd('/').split("/").last().takeIf { it.toIntOrNull() != null }
                ?.also { AHUCache.setJwxtStudentId(it) }
        }.onFailure { Log.w(TAG, "entry redirect resolve failed", it) }.getOrNull()
    }

    private fun looksLikeLoginPage(html: String): Boolean =
        html.contains("cas/login", ignoreCase = true) || html.contains("tologin", ignoreCase = true)

    /** mock 模式的演示数据（与真页同构：2 模块 + 子模块 + 三种状态）。 */
    private fun sampleCompletion() = ProgramCompletion(
        progressPercent = "34.8%",
        requiredCredits = 161.0,
        passedCredits = 56.0,
        takingCredits = 26.5,
        failedCredits = 105.0,
        programName = "2025级示例专业人才培养方案(主修)",
        programId = 0L,
        auditPublished = false,
        modules = listOf(
            CompletionModule(
                nameZh = "思想政治理论", requiredCredits = 18.0,
                passedCredits = 8.0, takingCredits = 4.0, failedCredits = 6.0,
                result = "UNPASSED",
                courses = listOf(
                    CompletionCourse("思想道德与法治", "GG61014", 3.0, true, "91", 91.0, 4.1, "PASSED", "TERM_1", null),
                    CompletionCourse("马克思主义基本原理", "GG61012", 3.0, true, null, null, null, "TAKING", "TERM_2", null),
                    CompletionCourse("形势与政策", "GG61001", 2.0, true, null, null, null, "UNREPAIRED", null, null)
                ),
                children = emptyList()
            ),
            CompletionModule(
                nameZh = "通识选修", requiredCredits = 10.0,
                passedCredits = 4.0, takingCredits = 0.0, failedCredits = 6.0,
                result = "UNPASSED",
                courses = emptyList(),
                children = listOf(
                    CompletionModule(
                        nameZh = "公共艺术类课程", requiredCredits = 2.0,
                        passedCredits = 0.0, takingCredits = 0.0, failedCredits = 2.0,
                        result = "UNPASSED",
                        courses = listOf(
                            CompletionCourse("艺术导论", "TS0001", 2.0, false, null, null, null, "UNREPAIRED", "TERM_4", null)
                        ),
                        children = emptyList()
                    )
                )
            )
        ),
        outerCourses = emptyList()
    )
}
