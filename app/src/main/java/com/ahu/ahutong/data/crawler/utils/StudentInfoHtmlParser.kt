package com.ahu.ahutong.data.crawler.utils

import com.ahu.ahutong.data.crawler.model.jwxt.StudentInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 教务学籍页（/student/for-std/student-info/info/{id}）的服务端渲染 HTML。
 *
 * 页面由多张 label/value 表格组成，只取展示所需的学籍、培养、录取与脱敏联系信息；
 * 证件号码、银行卡、家庭住址等不解析，避免把敏感信息带进页面与缓存。
 */
internal object StudentInfoHtmlParser {

    fun parse(html: String): StudentInfo? {
        val document = Jsoup.parse(html)
        if (document.selectFirst("#baseInfo") == null) return null

        val header = document.selectFirst(".base-header-right")
        val headerText = document.selectFirst(".base-header-wrapper")?.text().orEmpty()
        val name = document.selectFirst(".studentName")?.text()?.trim().orEmpty()
        val englishName = header?.children()?.firstOrNull()?.text()
            ?.replace(name, "")
            ?.let(::clean)

        val dark = document.selectFirst(".base-header .darkGrey")?.text().orEmpty()
        val genderEthnicity = dark.split("|").map { it.trim() }
        val gender = clean(genderEthnicity.getOrNull(0))
        val ethnicity = clean(
            genderEthnicity.getOrNull(1)?.replace(Regex("""\d{4}-\d{2}-\d{2}"""), "")
        )
        val birthDate = Regex("""\d{4}-\d{2}-\d{2}""").find(dark)?.value

        val base = parsePairs(document.getElementById("baseInfo"))
        val recruit = parsePairs(document.getElementById("recruitInfo"))
        val contact = parsePairs(document.getElementById("contactInfo")?.selectFirst("table"))
        val graduate = parsePairs(document.getElementById("graduateInfo"))

        fun baseValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { clean(base[it]) }
        fun recruitValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { clean(recruit[it]) }
        fun contactValue(key: String): String? = clean(contact[key])

        return StudentInfo(
            name = name,
            englishName = englishName,
            gender = gender,
            ethnicity = ethnicity,
            birthDate = birthDate,
            studentNumber = headerValue(headerText, "学号") ?: baseValue("学号"),
            politicalStatus = headerValue(headerText, "政治面貌"),
            trainingType = baseValue("培养类型"),
            department = baseValue("管理部门", "专业院系"),
            major = baseValue("专业"),
            majorDirection = baseValue("专业方向"),
            grade = baseValue("年级"),
            educationLevel = baseValue("学历层次"),
            studentCategory = baseValue("学生类别"),
            adminClass = baseValue("行政班"),
            program = baseValue("培养方案"),
            duration = baseValue("学制"),
            campus = baseValue("校区"),
            enrollmentStatus = baseValue("学籍状态"),
            registered = baseValue("是否需要注册"),
            inSchool = baseValue("是否在校"),
            counselor = baseValue("辅导员"),
            studyForm = baseValue("学习形式"),
            admissionDate = baseValue("入学日期"),
            expectedGraduationDate = clean(graduate["预计毕业日期"]),
            admissionYear = recruitValue("录取年份"),
            admissionBatch = recruitValue("录取批次"),
            admissionCategory = recruitValue("科类"),
            admissionProvince = recruitValue("生源省市"),
            highSchool = recruitValue("毕业中学"),
            phoneMasked = contactValue("手机"),
            email = contactValue("邮箱")
        )
    }

    private fun parsePairs(section: Element?): Map<String, String> {
        if (section == null) return emptyMap()
        val pairs = linkedMapOf<String, String>()
        section.select("tr").forEach { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            var index = 0
            while (index + 1 < cells.size) {
                val label = cells[index].text().trim()
                val value = cells[index + 1].text()
                if (label.isNotEmpty()) pairs[label] = value
                index += 2
            }
        }
        return pairs
    }

    private fun headerValue(headerText: String, label: String): String? =
        Regex("""$label：\s*(\S+)""").find(headerText)?.groupValues?.get(1)?.let(::clean)

    private fun clean(value: String?): String? {
        val normalized = value?.replace(Regex("""\s+"""), " ")?.trim()
        return normalized?.takeUnless { it.isEmpty() || it == "--" }
    }
}
