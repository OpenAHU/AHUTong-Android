package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.crawler.model.jwxt.CompletionCourse
import com.ahu.ahutong.data.crawler.model.jwxt.CompletionModule
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.state.ProgramCompletionViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 培养方案完成情况（本科专属）：顶部进度环 + 模块进度列表 + 课程明细 + 仅看未完成筛选。
 * 数据链：ProgramCompletionGateway（jwxt program-completion-preview 页 var model 解析）。
 * 状态文案口径：PASSED→已通过，TAKING→在读，其余（UNREPAIRED/FAILED/未知）→未通过。
 */
@Composable
fun ProgramCompletion(
    onBack: (() -> Unit)? = null,
    viewModel: ProgramCompletionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    AppPageScaffold(
        title = "培养方案",
        onBack = onBack,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        actions = listOf(
            TrailingAction(Icons.Rounded.Refresh, "刷新") { viewModel.refresh() }
        ),
        content = {
            val data = state.data
            when {
                state.loading -> AppStateCard.Inline(
                    horizontalInset = 0.dp,
                    loading = true,
                    title = "正在获取",
                    message = "首次加载需要读取教务培养方案完成情况，稍候…"
                )

                data == null -> AppStateCard.InlineError(
                    horizontalInset = 0.dp,
                    message = state.error ?: "未能获取培养方案完成情况",
                    onRetry = { viewModel.refresh() }
                )

                else -> {
                    CompletionSummaryCard(state)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "模块完成情况",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppFilterChip(
                            selected = state.incompleteOnly,
                            onClick = { viewModel.toggleIncompleteOnly() },
                            label = { Text("仅看未完成") }
                        )
                    }
                    val visibleModules = data.modules.mapNotNull {
                        it.filtered(state.incompleteOnly)
                    }
                    if (visibleModules.isEmpty()) {
                        AppStateCard.Inline(
                            horizontalInset = 0.dp,
                            title = "没有未完成的课程",
                            message = "当前筛选下所有模块均已完成，继续保持。"
                        )
                    }
                    // 大模块默认收起只给全览卡，点击展开详情（卡外缩进裸列表）
                    var expandedModules by rememberSaveable { mutableStateOf(setOf<String>()) }
                    for (module in visibleModules) {
                        val expanded = module.nameZh in expandedModules
                        ModuleOverviewCard(
                            module = module,
                            expanded = expanded,
                            onToggle = {
                                expandedModules = if (expanded) {
                                    expandedModules - module.nameZh
                                } else {
                                    expandedModules + module.nameZh
                                }
                            }
                        )
                        if (expanded) {
                            ModuleDetailList(module)
                        }
                    }
                    if (data.outerCourses.isNotEmpty()) {
                        OuterCoursesCard(data.outerCourses)
                    }
                    // 刷新失败但展示旧缓存时的提示
                    state.error?.let { err ->
                        Text(
                            "刷新失败：$err（当前展示的是缓存数据）",
                            color = 50.n1 withNight 70.n1,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CompletionSummaryCard(state: ProgramCompletionViewModel.UiState) {
    val data = state.data ?: return
    AppSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val percent = data.progressPercent.removeSuffix("%").toFloatOrNull()?.div(100f)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                CircularProgressIndicator(
                    progress = { percent ?: 0f },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp
                )
                Text(
                    data.progressPercent.ifBlank { "--" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    data.programName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "已通过 ${data.passedCredits.trimZero()} / 要求 ${data.requiredCredits.trimZero()} 学分",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "在读 ${data.takingCredits.trimZero()} · 未通过 ${data.failedCredits.trimZero()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = 50.n1 withNight 70.n1
                )
            }
        }
        if (!data.auditPublished) {
            Text(
                "教务审核结果未发布，完成情况以教务系统为准",
                style = MaterialTheme.typography.labelSmall,
                color = 50.n1 withNight 70.n1
            )
        }
        if (state.fetchedAt > 0) {
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(state.fetchedAt))
            Text(
                "数据更新于 $time",
                style = MaterialTheme.typography.labelSmall,
                color = 50.n1 withNight 70.n1
            )
        }
    }
}

/** 大模块全览卡（可点击展开/收起）：标题 + 进度条 + 学分统计，不含课程明细。
 *  点击走 AppCard 的 onClick（主题原生点击通道：Radiant 下无 ripple、不裁玻璃阴影）；
 *  切勿在 AppSectionCard 外层 clip+clickable——clip 会把 Radiant 玻璃卡的阴影裁成硬边。 */
@Composable
private fun ModuleOverviewCard(
    module: CompletionModule,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        // AppCard 本身不提供子项间距：标题行 / 进度条 / 学分统计需要自己留白
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    module.nameZh,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (module.result != null && module.result != "PASSED") {
                    Text(
                        "未通过",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor("UNREPAIRED")
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = 50.n1 withNight 70.n1
                )
            }
            val progress = if (module.requiredCredits > 0) {
                (module.passedCredits / module.requiredCredits).toFloat().coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "要求 ${module.requiredCredits.trimZero()} 学分 · 已通过 ${module.passedCredits.trimZero()} · " +
                    "在读 ${module.takingCredits.trimZero()} · 未通过 ${module.failedCredits.trimZero()}",
                style = MaterialTheme.typography.bodySmall,
                color = 50.n1 withNight 70.n1
            )
        }
    }
}

/** 模块详情：卡外缩进裸列表。子模块为加粗小节标题（不套卡），课程行随层级缩进。 */
@Composable
private fun ModuleDetailList(module: CompletionModule) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (course in module.courses) {
            CourseRow(course, depth = 0)
        }
        for (child in module.children) {
            BareModuleSection(child, depth = 0)
        }
    }
}

/** 子模块小节（裸列表内）：缩进标题 + 统计行 + 课程行，递归支持更深层级。 */
@Composable
private fun BareModuleSection(module: CompletionModule, depth: Int) {
    val indent = 12.dp * (depth + 1)
    Text(
        module.nameZh,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = indent, top = 8.dp)
    )
    Text(
        "要求 ${module.requiredCredits.trimZero()} · 已通过 ${module.passedCredits.trimZero()} · " +
            "在读 ${module.takingCredits.trimZero()} · 未通过 ${module.failedCredits.trimZero()}",
        style = MaterialTheme.typography.labelSmall,
        color = 50.n1 withNight 70.n1,
        modifier = Modifier.padding(start = indent)
    )
    for (course in module.courses) {
        CourseRow(course, depth = depth + 1)
    }
    for (child in module.children) {
        BareModuleSection(child, depth + 1)
    }
}

@Composable
private fun CourseRow(course: CompletionCourse, depth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (depth > 0) 12.dp else 0.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(course.nameZh, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(course.code)
                    append(" · ")
                    append(course.credits.trimZero())
                    append(" 学分")
                    append(if (course.compulsory) " · 必修" else " · 选修")
                    formatTerms(course.termsContent)?.let { append(" · ").append(it) }
                    course.remark?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = 50.n1 withNight 70.n1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val grade = course.gradeStr ?: course.score?.trimZero()
            if (!grade.isNullOrBlank()) {
                Text(
                    grade + (course.gp?.let { " / 绩点 ${it.trimZero()}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                statusLabel(course.status),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(course.status)
            )
        }
    }
}

@Composable
private fun OuterCoursesCard(courses: List<CompletionCourse>) {
    AppSectionCard {
        Text(
            "计划外完成情况（学分认定/替代）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        for (course in courses) {
            CourseRow(course, depth = 0)
        }
    }
}

/** "仅看未完成"：过滤掉已通过的课程；模块过滤后无课程且无未完成子模块则整个隐藏。 */
private fun CompletionModule.filtered(incompleteOnly: Boolean): CompletionModule? {
    if (!incompleteOnly) return this
    val keptCourses = courses.filter { it.status != "PASSED" }
    val keptChildren = children.mapNotNull { it.filtered(true) }
    return if (keptCourses.isEmpty() && keptChildren.isEmpty()) null
    else copy(courses = keptCourses, children = keptChildren)
}

private fun statusLabel(status: String): String = when (status) {
    "PASSED" -> "已通过"
    "TAKING" -> "在读"
    else -> "未通过"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "PASSED" -> Color(0xFF2E7D32) withNight Color(0xFF81C784)
    "TAKING" -> Color(0xFF1976D2) withNight Color(0xFF64B5F6)
    else -> Color(0xFFC62828) withNight Color(0xFFE57373)
}

/** "TERM_2,TERM_5" → "建议第2/5学期"。 */
private fun formatTerms(terms: String?): String? {
    if (terms.isNullOrBlank()) return null
    val termsList = terms.split(",").mapNotNull {
        it.trim().removePrefix("TERM_").toIntOrNull()
    }
    if (termsList.isEmpty()) return null
    return "建议第" + termsList.joinToString("/") + "学期"
}

/** 56.0 → "56"，26.5 → "26.5"。 */
private fun Double.trimZero(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
