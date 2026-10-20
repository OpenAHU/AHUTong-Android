package com.ahu.ahutong.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.core.designsystem.R as DesignSystemR
import com.ahu.ahutong.data.crawler.model.jwxt.StudentInfo
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.state.ScheduleViewModel

@Composable
fun ProfileScreen(
    scheduleViewModel: ScheduleViewModel,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val studentInfo by viewModel.studentInfo.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()

    val user = SessionStore.currentUser()
    val sessionName = user?.name?.takeIf(String::isNotBlank) ?: "未登录"
    val sessionStudentId = user?.xh?.takeIf(String::isNotBlank)
    val isGraduate = user?.academicAccountType == AcademicAccountType.POSTGRADUATE
    val accountType = if (isGraduate) "研究生账号" else "本科生账号"
    val name = studentInfo?.name?.takeIf(String::isNotBlank) ?: sessionName
    val studentNumber = studentInfo?.studentNumber?.takeIf(String::isNotBlank) ?: sessionStudentId

    val weekLabel = when {
        scheduleConfig?.isInSemester == true &&
            (scheduleConfig?.week ?: 0) > 0 -> "第${scheduleConfig?.week}周"
        scheduleConfig != null -> "假期中"
        else -> "未确认"
    }
    val semesterKey = remember { CurrentWeekResolver.getCachedSemesterKey() }
    val schoolYearLabel = semesterKey?.schoolYear ?: "未确认"
    val schoolTermLabel = semesterKey?.schoolTerm?.takeIf(String::isNotBlank)
        ?.let { "第${it}学期" } ?: "未确认"

    AppPageScaffold(
        title = "个人信息",
        onBack = onBack,
        trailingContent = {
            AppTitleIconButton(
                onClick = { viewModel.refresh(force = true) },
                contentDescription = "刷新个人信息",
                icon = DesignSystemR.drawable.ic_refresh,
                loading = refreshing
            )
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            AppSectionCard(contentPadding = PaddingValues(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = listOfNotNull(
                                studentNumber?.let { "学号 $it" },
                                accountType
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            val info = studentInfo
            if (info != null) {
                StudentInfoSections(info)
            } else {
                AppSectionCard {
                    ProfileSectionTitle("学籍信息")
                    if (refreshing) {
                        LoadingRow("正在读取学籍信息…")
                    } else {
                        Text(
                            text = if (isGraduate) {
                                "研究生账号暂不支持读取本科学籍页。"
                            } else {
                                "暂未获取到学籍信息，可以点右上角刷新。"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (profiles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        profiles.forEachIndexed { index, profile ->
                            if (index > 0) Spacer(Modifier.height(8.dp))
                            Text(
                                text = profile.trainingType.ifBlank { "主修" },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            ProfileInfoRow("学院", profile.department.ifBlank { "未获取" })
                            ProfileInfoRow("专业", profile.major.ifBlank { "未获取" })
                        }
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!isGraduate) {
                AppSectionCard {
                    ProfileSectionTitle("学期")
                    ProfileInfoRow("学年", "$schoolYearLabel 学年")
                    ProfileInfoRow("学期", schoolTermLabel)
                    ProfileInfoRow("当前教学周", weekLabel)
                }
            }

            Text(
                text = "姓名、学号与学籍信息来自教务学籍页；学期与周次来自课表设置。以上信息仅在本机展示。",
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

@Composable
private fun StudentInfoSections(info: StudentInfo) {
    ProfileSection("身份信息") {
        ProfileInfoRow("姓名", info.name.ifBlank { "未获取" })
        info.englishName?.let { ProfileInfoRow("姓名拼音", it) }
        listOfNotNull(info.gender, info.ethnicity).joinToString(" · ")
            .takeIf(String::isNotBlank)?.let { ProfileInfoRow("性别 / 民族", it) }
        info.birthDate?.let { ProfileInfoRow("出生日期", it) }
        info.politicalStatus?.let { ProfileInfoRow("政治面貌", it) }
    }

    ProfileSection("学籍信息") {
        info.studentNumber?.let { ProfileInfoRow("学号", it) }
        info.grade?.let { ProfileInfoRow("年级", it) }
        info.educationLevel?.let { ProfileInfoRow("学历层次", it) }
        info.studentCategory?.let { ProfileInfoRow("学生类别", it) }
        info.trainingType?.let { ProfileInfoRow("培养类型", it) }
        info.enrollmentStatus?.let { ProfileInfoRow("学籍状态", it) }
        info.inSchool?.let { ProfileInfoRow("是否在校", it) }
        info.campus?.let { ProfileInfoRow("校区", it) }
        info.admissionDate?.let { ProfileInfoRow("入学日期", it) }
        info.expectedGraduationDate?.let { ProfileInfoRow("预计毕业", it) }
        info.adminClass?.let { ProfileInfoRow("行政班", it) }
        info.counselor?.let { ProfileInfoRow("辅导员", it) }
    }

    ProfileSection("培养信息") {
        info.department?.let { ProfileInfoRow("管理部门", it) }
        info.major?.let { ProfileInfoRow("专业", it) }
        info.majorDirection?.let { ProfileInfoRow("专业方向", it) }
        info.program?.let { ProfileInfoRow("培养方案", it) }
        info.duration?.let { ProfileInfoRow("学制", it) }
        info.studyForm?.let { ProfileInfoRow("学习形式", it) }
    }

    val admissionRows = listOfNotNull(
        info.admissionYear?.let { "录取年份" to it },
        info.admissionBatch?.let { "录取批次" to it },
        info.admissionCategory?.let { "科类" to it },
        info.admissionProvince?.let { "生源省市" to it },
        info.highSchool?.let { "毕业中学" to it }
    )
    if (admissionRows.isNotEmpty()) {
        ProfileSection("录取信息") {
            admissionRows.forEach { (label, value) -> ProfileInfoRow(label, value) }
        }
    }

    val contactRows = listOfNotNull(
        info.phoneMasked?.let { "手机" to it },
        info.email?.let { "邮箱" to it }
    )
    if (contactRows.isNotEmpty()) {
        ProfileSection("联系信息") {
            contactRows.forEach { (label, value) -> ProfileInfoRow(label, value) }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppSectionCard {
        ProfileSectionTitle(title)
        content()
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppCircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
