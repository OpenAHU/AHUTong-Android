package com.ahu.ahutong.data.crawler.model.jwxt

import java.io.Serializable

/** 教务「学籍信息」页解析出的字段；证件号、银行卡、家庭住址等敏感字段不进入模型。 */
data class StudentInfo(
    val name: String = "",
    val englishName: String? = null,
    val gender: String? = null,
    val ethnicity: String? = null,
    val birthDate: String? = null,
    val studentNumber: String? = null,
    val politicalStatus: String? = null,
    val trainingType: String? = null,
    val department: String? = null,
    val major: String? = null,
    val majorDirection: String? = null,
    val grade: String? = null,
    val educationLevel: String? = null,
    val studentCategory: String? = null,
    val adminClass: String? = null,
    val program: String? = null,
    val duration: String? = null,
    val campus: String? = null,
    val enrollmentStatus: String? = null,
    val registered: String? = null,
    val inSchool: String? = null,
    val counselor: String? = null,
    val studyForm: String? = null,
    val admissionDate: String? = null,
    val expectedGraduationDate: String? = null,
    val admissionYear: String? = null,
    val admissionBatch: String? = null,
    val admissionCategory: String? = null,
    val admissionProvince: String? = null,
    val highSchool: String? = null,
    val phoneMasked: String? = null,
    val email: String? = null
) : Serializable
