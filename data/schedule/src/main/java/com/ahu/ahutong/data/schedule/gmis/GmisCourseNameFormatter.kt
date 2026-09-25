package com.ahu.ahutong.data.schedule.gmis

/** Remove the cohort label from presentation only; keep the GMIS response and cache intact. */
object GmisCourseNameFormatter {
    private val cohortInBrackets = Regex("[（(【\\[]\\s*26级研究生\\s*[）)】\\]]")
    private val edgeSeparators = Regex("^[\\s:：·—–-]+|[\\s:：·—–-]+$")

    fun display(raw: String): String = raw
        .replace(cohortInBrackets, "")
        .replace("26级研究生", "")
        .replace(edgeSeparators, "")
        .trim()
        .ifBlank { "未命名课程" }
}
