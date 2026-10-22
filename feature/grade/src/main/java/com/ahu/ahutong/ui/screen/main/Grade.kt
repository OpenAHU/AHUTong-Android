package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.feature.grade.R
import com.ahu.ahutong.data.grade.GradeEvaluationGate
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.GradeViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Cancel
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Search
import com.ahu.ahutong.core.designsystem.R as DesignSystemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Grade(
    gradeViewModel: GradeViewModel = hiltViewModel(),
    onNavigateToEvaluation: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    DisposableEffect(gradeViewModel) {
        onDispose { gradeViewModel.onPresetSurfaceDisposed() }
    }
    val grade = gradeViewModel.grade
    val gpaRankInfo = gradeViewModel.gpaRankInfo
    val errorMessage = gradeViewModel.errorMessage
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val mockRefreshRevision by gradeViewModel.mockRefreshRevisions.collectAsState(0L)

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var termMenuExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        searchQuery = ""
    }

    LaunchedEffect(gradeViewModel) {
        gradeViewModel.loadOnEnter()
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && gradeViewModel.usesMockData) {
            gradeViewModel.getGarde(isRefresh = true)
            gradeViewModel.getGpaRank()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            gradeViewModel.errorMessage = null
        }
    }

    val gradeData = gradeViewModel.grade?.termGradeList?.find {
        it.schoolYear == gradeViewModel.schoolYear &&
                it.term == gradeViewModel.schoolTerm
    }

    val currentRank = gpaRankInfo?.gpaSemesterSubs?.find {
        it.semesterId == gradeData?.gradeList?.firstOrNull()?.semesterId
    }

    val trimmedQuery = if (searchExpanded) searchQuery.trim() else ""

    fun fuzzyContains(text: String, query: String): Boolean {
        if (query.isBlank()) return false
        val q = query.filterNot { it.isWhitespace() }
        if (q.isEmpty()) return false
        val pattern = q.map { Regex.escape(it.toString()) }.joinToString(".*")
        return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    val searchResultsByTerm = gradeViewModel.grade?.termGradeList
        ?.mapNotNull { term ->
            val matches = term.gradeList
                ?.filter { item ->
                    val q = trimmedQuery
                    q.isNotEmpty() && (
                            fuzzyContains(item.course ?: "", q) ||
                                    fuzzyContains(item.courseNum ?: "", q) ||
                                    fuzzyContains(item.courseNature ?: "", q)
                            )
                }
                .orEmpty()
            if (matches.isEmpty()) null else term to matches
        }
        .orEmpty()

    val allTerms = gradeViewModel.grade?.termGradeList
        ?.sortedWith(
            compareByDescending<Grade.TermGradeListBean> {
                it.schoolYear.substringBefore("-").toIntOrNull() ?: 0
            }.thenByDescending {
                it.term.toIntOrNull() ?: 0
            }
        )
        .orEmpty()
    val selectedTermText = gradeViewModel.schoolYear?.let { schoolYear ->
        gradeViewModel.schoolTerm?.let { schoolTerm ->
            "$schoolYear 第${schoolTerm}学期"
        }
    } ?: "选择学期"

    val pageHeader: @Composable ColumnScope.() -> Unit = {
        // Profile selector - shown when student has multiple profiles (micro-major/minor)
        if (!searchExpanded && gradeViewModel.studentProfiles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gradeViewModel.studentProfiles.forEachIndexed { index, profile ->
                        AppFilterChip(
                            selected = gradeViewModel.selectedProfileIndex == index,
                            onClick = { gradeViewModel.selectProfile(index) },
                            label = {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            }

        // 学期选择已统一到头部漏斗菜单（GradeTermMenuButton），全主题一致

        if (!searchExpanded) {
                gradeViewModel.presetCandidates.firstOrNull()?.let { candidate ->
                    LaunchedEffect(candidate.opportunityId, candidate.presetId) {
                        gradeViewModel.onPresetCandidateVisible(candidate)
                    }
                    Text(
                        text = "使用常用条件",
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(90.a1)
                            .clickable { gradeViewModel.applyPresetCandidate(candidate) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = 0.n1,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (!searchExpanded) {
                val summary: @Composable ColumnScope.() -> Unit = {
                    val loadingRank = gradeViewModel.rankLoading ||
                        (gradeViewModel.isLoading && gpaRankInfo == null)
                    val rankPlaceholder = if (loadingRank) "加载中" else "暂无"
                    val rankMsg = gradeViewModel.rankEmptyMessage
                    if (loadingRank) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppCircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                text = if (gpaRankInfo == null) "绩点及排名加载中" else "正在更新绩点及排名",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!rankMsg.isNullOrBlank()) {
                        Text(
                            text = rankMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    listOf(
                        "本学期平均绩点" to gradeViewModel.termGradePointAverage,
                        "全程平均绩点" to if (gpaRankInfo != null) gradeViewModel.totalGradePointAverage else rankPlaceholder,
                        "全程专业排名" to (gpaRankInfo?.let { "${it.majorRank}/${it.majorHeadCount}" } ?: rankPlaceholder),
                        "该学期专业排名" to (currentRank?.let { "${it.majorRank}/${gpaRankInfo?.majorHeadCount ?: rankPlaceholder}" } ?: rankPlaceholder),
                        "最后更新时间" to (gpaRankInfo?.updatedDateTimeStr?.takeIf { it.isNotBlank() } ?: rankPlaceholder)
                    ).forEach { (title, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(title, color = 0.n1 withNight 100.n1, style = MaterialTheme.typography.titleMedium)
                            Text(value, color = 0.n1 withNight 100.n1, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                AppSectionCard(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = summary
                )
            }

    }

    val pageContent: LazyListScope.() -> Unit = {
        if (!searchExpanded) {
            item(key = "header") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = pageHeader
                )
            }
        }
        if (searchExpanded && trimmedQuery.isNotBlank()) {
            searchResultsByTerm.forEachIndexed { termIndex, (term, matches) ->
                item(key = "search-term-$termIndex") {
                    Text(
                        text = "${term.schoolYear} 第${term.term}学期",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = 0.n1 withNight 100.n1,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                itemsIndexed(matches, key = { index, _ -> "search-grade-$termIndex-$index" }) { _, item ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        GradeCard(item = item, onNavigateToEvaluation = onNavigateToEvaluation)
                    }
                }
            }
        } else if (!searchExpanded && gradeViewModel.isLoading && gradeViewModel.grade == null) {
            item(key = "loading") {
                AppStateCard.Loading(modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else if (!searchExpanded && gradeData != null && gradeData.gradeList.isNotEmpty()) {
            itemsIndexed(gradeData.gradeList, key = { index, _ -> "grade-$selectedTermText-$index" }) { _, item ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GradeCard(item = item, onNavigateToEvaluation = onNavigateToEvaluation)
                }
            }
        } else if (!searchExpanded) {
            item(key = "empty") {
                val profile = gradeViewModel.studentProfiles.getOrNull(gradeViewModel.selectedProfileIndex)
                val message = if (gradeViewModel.studentProfiles.size > 1 && profile != null) {
                    "「${profile.displayName}」暂无成绩"
                } else "该学期目前没有任何成绩"
                AppStateCard.Empty(message = message, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    val refreshGrades = {
        gradeViewModel.onManualRefresh()
        gradeViewModel.refreshGrade()
    }
    AppPageScaffold(
        title = stringResource(id = R.string.grade),
        onBack = onBack,
        subtitle = selectedTermText,
        modifier = Modifier.fillMaxSize(),
        search = SecondarySearchState(
            query = searchQuery,
            visible = searchExpanded,
            placeholder = "搜索课程",
            onQueryChange = { searchQuery = it },
            onClose = {
                searchExpanded = false
                searchQuery = ""
            },
            onSubmit = {}
        ),
        trailingContent = {
            GradeTermMenuButton(
                allTerms = allTerms,
                selectedTermText = selectedTermText,
                expanded = termMenuExpanded,
                onExpandedChange = { termMenuExpanded = it },
                onSelect = { year, term ->
                    gradeViewModel.selectTerm(year, term)
                    termMenuExpanded = false
                }
            )
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_refresh,
                contentDescription = "刷新成绩",
                onClick = refreshGrades
            )
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_find,
                contentDescription = "搜索成绩",
                onClick = { searchExpanded = true }
            )
        },
        bottomPadding = 48.dp,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        lazyContent = pageContent
    )
}

@Composable
private fun GradeTermMenuButton(
    allTerms: List<Grade.TermGradeListBean>,
    selectedTermText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String, String) -> Unit
) {
    Box {
        AppTitleIconButton(
            icon = DesignSystemR.drawable.ic_filter,
            contentDescription = "选择学期：$selectedTermText",
            onClick = { onExpandedChange(!expanded) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(99.n1 withNight 10.n1)
        ) {
            allTerms.forEach { term ->
                DropdownMenuItem(
                    text = { Text("${term.schoolYear} 第${term.term}学期") },
                    onClick = { onSelect(term.schoolYear, term.term) }
                )
            }
        }
    }
}

@Composable
private fun GradeCard(
    item: Grade.TermGradeListBean.GradeListBean,
    onNavigateToEvaluation: () -> Unit
) {
    val needsEvaluation = GradeEvaluationGate.isRequiredPayload(item.grade) ||
        GradeEvaluationGate.isRequiredPayload(item.gradeDetail)
    val gradeText = item.grade.stripHtml()
    val gradeDetail = item.gradeDetail.stripHtml()

    AppCard(
        modifier = Modifier
            .fillMaxWidth(),
        shape = SmoothRoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = item.course ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        if (needsEvaluation) {
            val linkColor = 40.a1 withNight 80.a1
            Text(
                text = buildAnnotatedString {
                    append("成绩: ")
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(GradeEvaluationGate.MESSAGE)
                    }
                    append("    绩点: ${item.gradePoint}    学分: ${item.credit}")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onNavigateToEvaluation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = "成绩: $gradeText    绩点: ${item.gradePoint}    学分: ${item.credit}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Text(
            text = "${item.courseNature ?: ""} (${item.courseNum ?: ""})",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!needsEvaluation && !gradeDetail.isNullOrBlank()) {
            Text(
                text = gradeDetail,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        }
    }
}

private fun String?.stripHtml(): String {
    return orEmpty()
        .replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("<[^>]*>"), "")
        .trim()
}
