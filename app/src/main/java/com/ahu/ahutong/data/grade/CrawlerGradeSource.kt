package com.ahu.ahutong.data.grade

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.utils.GpaRankHtmlParser
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import org.jsoup.Jsoup

/**
 * Grade crawler sink — multi-profile grade + GPA rank HTML parsing inlined from CrawlerDataSource.
 */
@Singleton
class CrawlerGradeSource @Inject constructor() : GradeCrawlerSource {
    private val tag = "CrawlerGradeSource"
    private val gson = Gson()

    override suspend fun fetchGrade(): AppResult<Grade> {
        return try {
            val profiles = getGradeStudentProfiles()
            val perProfileGrades = profiles.map { profile ->
                try {
                    buildGradeForId(profile.id)
                } catch (e: Exception) {
                    Log.w(tag, "getGrade failed for id=${profile.id}", e)
                    null
                }
            }

            val allGradeLists = perProfileGrades
                .filterNotNull()
                .flatMap { it.termGradeList ?: emptyList() }
                .toMutableList()

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
            AHUCache.savePerProfileGrades(profiles.zip(perProfileGrades).toMap())
            AppResult.success(grade)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取成绩失败", t)
        }
    }

    override suspend fun fetchGpaRank(studentId: String): AppResult<GpaRankInfo> {
        return try {
            val htmlResponse = JwxtApi.API.getGpaRankPage(studentId)
            if (!htmlResponse.isSuccessful || htmlResponse.body() == null) {
                return AppResult.error("获取成绩排名页面失败", code = htmlResponse.code())
            }
            val html = htmlResponse.body()!!.string()
            val jsObject = GpaRankHtmlParser.extractModelObject(html)
            val json = convertJsToJson(jsObject)
            val gpaRankInfo = gson.fromJson(json, GpaRankInfo::class.java)
            AppResult.success(gpaRankInfo)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取成绩排名失败", t)
        }
    }

    private fun convertJsToJson(js: String): String = js.replace(Regex("'"), "\"")

    private suspend fun buildGradeForId(id: String): Grade? {
        val data = JwxtApi.API.getGrade(id)
        val termGradeLists = mutableListOf<Grade.TermGradeListBean>()

        data.semesterId2studentGrades?.values?.forEach { gradeList ->
            var termName: String? = null
            val newGradeList = mutableListOf<Grade.TermGradeListBean.GradeListBean>()

            gradeList.forEach { item ->
                termName = termName ?: item.semesterName
                val grade = Grade.TermGradeListBean.GradeListBean()
                grade.course = item.courseName
                grade.credit = item.credits.toString()
                grade.grade = item.gaGrade
                grade.gradePoint = item.gp.toString()
                grade.courseNature = item.courseType
                grade.courseNum = item.courseCode
                grade.semesterId = item.semesterId!!
                grade.gradeDetail = item.gradeDetail
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
                        "%.2f".format(
                            totalGradePointWeighted / termGradeList.termTotalCredit.toDouble(),
                        )
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

    private suspend fun getGradeStudentProfiles(): List<GradeStudentProfile> {
        val cached = AHUCache.getGradeStudentProfiles()
        if (cached.isNotEmpty()) return cached

        try {
            val redirectUrl = JwxtApi.API.getGrade().raw().request.url.toString()
            val lastSegment = redirectUrl.split("/").last()
            if (lastSegment.toIntOrNull() != null) {
                val list = listOf(
                    GradeStudentProfile(
                        id = lastSegment,
                        trainingType = "主修",
                        department = "",
                        major = "",
                    ),
                )
                AHUCache.setGradeStudentProfiles(list)
                AHUCache.setJwxtStudentId(lastSegment)
                return list
            }
        } catch (_: Exception) {
        }

        try {
            val htmlResponse = JwxtApi.API.getGrade()
            if (htmlResponse.isSuccessful && htmlResponse.body() != null) {
                val html = htmlResponse.body()!!.string()
                val profiles = parseGradeStudentProfiles(html)
                if (profiles.isNotEmpty()) {
                    AHUCache.setGradeStudentProfiles(profiles)
                    AHUCache.setJwxtStudentId(profiles.first().id)
                    return profiles
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse grade student profiles", e)
        }

        return emptyList()
    }

    private fun parseGradeStudentProfiles(html: String): List<GradeStudentProfile> {
        val doc = Jsoup.parse(html)
        val panels = doc.select(".student-panel-block")
        if (panels.isEmpty()) return emptyList()

        return panels.mapNotNull { panel ->
            val button = panel.select("button[onclick*=myFunction]").first()
            val id = button?.attr("value") ?: return@mapNotNull null

            val dds = panel.select("dd")
            val trainingType = dds.getOrNull(0)?.text()?.trim() ?: ""
            val department = dds.getOrNull(1)?.text()?.trim() ?: ""
            val major = dds.getOrNull(2)?.text()?.trim() ?: ""

            GradeStudentProfile(
                id = id,
                trainingType = trainingType,
                department = department,
                major = major,
            )
        }
    }
}
