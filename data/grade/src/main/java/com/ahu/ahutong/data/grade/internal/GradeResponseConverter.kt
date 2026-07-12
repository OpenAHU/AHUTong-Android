package com.ahu.ahutong.data.grade.internal

import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.ahu.ahutong.data.model.Grade
import com.google.gson.Gson

/**
 * Converts native/crawler [GradeResponse] JSON into domain [Grade].
 * Logic ported from SdkDataSource.convertGradeResponse.
 */
internal object GradeResponseConverter {
    private val gson = Gson()

    fun fromJson(json: String): Grade {
        val data = gson.fromJson(json, GradeResponse::class.java)
            ?: error("empty grade response")
        return fromResponse(data)
    }

    fun fromResponse(data: GradeResponse): Grade {
        val map = hashMapOf<String, Grade.TermGradeListBean>()

        data.semesterId2studentGrades?.values?.forEach { gradeList ->
            val newGradeList = mutableListOf<Grade.TermGradeListBean.GradeListBean>()
            var termName: String? = null

            gradeList.forEach { item ->
                termName = termName ?: item.semesterName
                val grade = Grade.TermGradeListBean.GradeListBean()
                grade.course = item.courseName ?: ""
                grade.credit = (item.credits ?: 0.0).toString()
                grade.grade = item.gaGrade ?: ""
                grade.gradePoint = (item.gp ?: 0.0).toString()
                grade.courseNature = item.courseType ?: ""
                grade.courseNum = item.courseCode ?: ""
                grade.gradeDetail = item.gradeDetail ?: ""
                grade.semesterId = item.semesterId ?: 0
                newGradeList.add(grade)
            }

            termName?.let { name ->
                val names = name.split("-")
                if (names.size < 3) {
                    return@forEach
                }

                val termGradeList = Grade.TermGradeListBean()
                termGradeList.gradeList = newGradeList
                termGradeList.term = names[2]
                termGradeList.schoolYear = "${names[0]}-${names[1]}"
                termGradeList.termGradePoint = newGradeList.sumOf {
                    it.grade?.toDoubleOrNull() ?: 0.0
                }.toString()
                termGradeList.termTotalCredit = newGradeList.sumOf {
                    it.credit?.toDoubleOrNull() ?: 0.0
                }.toString()
                val totalGradePointWeighted = newGradeList.sumOf {
                    (it.gradePoint?.toDoubleOrNull() ?: 0.0) * (it.credit?.toDoubleOrNull() ?: 0.0)
                }
                termGradeList.termGradePointAverage =
                    if ((termGradeList.termTotalCredit?.toDoubleOrNull() ?: 0.0) > 0) {
                        "%.2f".format(
                            totalGradePointWeighted / termGradeList.termTotalCredit.toDouble(),
                        )
                    } else {
                        "0.0"
                    }
                map[name] = termGradeList
            }
        }

        val termGradeList = map.values.toList()
        val grade = Grade()
        grade.totalCredit = termGradeList.sumOf {
            it.termTotalCredit?.toDoubleOrNull() ?: 0.0
        }.toString()
        val weightedGradePointSum = termGradeList.sumOf {
            val avg = it.termGradePointAverage?.toDoubleOrNull() ?: 0.0
            val credit = it.termTotalCredit?.toDoubleOrNull() ?: 0.0
            avg * credit
        }
        grade.totalGradePoint = weightedGradePointSum.toString()
        grade.totalGradePointAverage =
            if ((grade.totalCredit?.toDoubleOrNull() ?: 0.0) > 0) {
                "%.2f".format(weightedGradePointSum / grade.totalCredit.toDouble())
            } else {
                "0.0"
            }
        grade.termGradeList = termGradeList
        return grade
    }
}
