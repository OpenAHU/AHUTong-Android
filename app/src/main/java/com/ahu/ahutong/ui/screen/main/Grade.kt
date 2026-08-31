package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.GradeEvaluationGate
import com.ahu.ahutong.data.crawler.model.jwxt.CourseGrade
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.components.GlassCard
import com.ahu.ahutong.ui.components.SecondaryPageScaffold
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.state.GradeViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Grade(
    gradeViewModel: GradeViewModel = hiltViewModel(),
    onNavigateToEvaluation: () -> Unit = {}
) {
    DisposableEffect(gradeViewModel) {
        onDispose { gradeViewModel.onPresetSurfaceDisposed() }
    }
    val behaviorReporter = rememberBehaviorActionReporter()
    val grade = gradeViewModel.grade
    val gpaRankInfo = gradeViewModel.gpaRankInfo
    val errorMessage = gradeViewModel.errorMessage
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var termMenuExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        searchQuery = ""
    }

    LaunchedEffect(Unit) {
        if (grade == null) gradeViewModel.getGarde()
        if (gpaRankInfo == null) gradeViewModel.getGpaRank()
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
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

    if (isRadiantUi) {
        val allTerms = gradeViewModel.grade?.termGradeList
            ?.sortedWith(
                compareByDescending<Grade.TermGradeListBean> {
                    it.schoolYear.substringBefore("-").toIntOrNull() ?: 0
                }.thenByDescending {
                    it.term.toIntOrNull() ?: 0
                }
            )
            .orEmpty()
        val selectedTermText =
            "${gradeViewModel.schoolYear} 第${gradeViewModel.schoolTerm}学期"
        SecondaryPageScaffold(
            title = stringResource(id = R.string.grade),
            subtitle = selectedTermText,
            actions = emptyList(),
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                behaviorReporter.organic(AppActionId.MANUAL_REFRESH_GRADE)
                                gradeViewModel.refreshGrade()
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = "刷新成绩",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (!searchExpanded && gradeViewModel.studentProfiles.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            gradeViewModel.studentProfiles.forEachIndexed { index, profile ->
                                FilterChip(
                                    selected = gradeViewModel.selectedProfileIndex == index,
                                    onClick = { gradeViewModel.selectProfile(index) },
                                    label = {
                                        Text(
                                            text = profile.displayName,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = 80.a1 withNight 50.a1,
                                        selectedLabelColor = 100.n1 withNight 0.n1,
                                        containerColor = 90.n1 withNight 20.n1,
                                        labelColor = 10.n1 withNight 90.n1
                                    ),
                                    shape = ContinuousCapsule
                                )
                            }
                        }
                    }

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

                    when {
                        trimmedQuery.isNotBlank() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                searchResultsByTerm.forEach { (term, items) ->
                                    Text(
                                        text = "${term.schoolYear} 第${term.term}学期",
                                        color = 0.n1 withNight 100.n1,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items.forEach { item ->
                                            GradeCard(
                                                item = item,
                                                onNavigateToEvaluation = onNavigateToEvaluation
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        gradeData != null && gradeData.gradeList.isNotEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                if (!searchExpanded) {
                                    GlassCard(
                                        containerColor = 100.n1 withNight 20.n1,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val rankMsg = gradeViewModel.rankEmptyMessage
                                            if (gpaRankInfo == null && !rankMsg.isNullOrBlank()) {
                                                Text(
                                                    text = rankMsg,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = 50.n1 withNight 70.n1
                                                )
                                            }
                                            val infoList = listOf(
                                                "本学期平均绩点" to gradeViewModel.termGradePointAverage,
                                                "全程平均绩点" to gradeViewModel.totalGradePointAverage,
                                                "全程专业排名" to ((gpaRankInfo?.majorRank ?: "暂无").toString() + "/" + (gpaRankInfo?.majorHeadCount ?: "暂无")),
                                                "该学期专业排名" to ((currentRank?.majorRank ?: "暂无").toString() + "/" + (gpaRankInfo?.majorHeadCount ?: "暂无")),
                                                "最后更新时间" to (gpaRankInfo?.updatedDateTimeStr ?: "暂无")
                                            )
                                            infoList.forEach { (title, value) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = title,
                                                        color = 0.n1 withNight 100.n1,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = value,
                                                        color = 0.n1 withNight 100.n1,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    gradeData.gradeList.forEach { item ->
                                        GradeCard(
                                            item = item,
                                            onNavigateToEvaluation = onNavigateToEvaluation
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            val emptyMsg = if (gradeViewModel.studentProfiles.size > 1) {
                                val p = gradeViewModel.studentProfiles.getOrNull(gradeViewModel.selectedProfileIndex)
                                if (p != null) "「${p.displayName}」暂无成绩" else "该学期目前没有任何成绩"
                            } else {
                                "该学期目前没有任何成绩"
                            }
                            Text(
                                text = emptyMsg,
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.titleLarge,
                                color = 50.n1 withNight 70.n1
                            )
                        }
                    }
                }
            }
        )
    } else {
        Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
        ) {
            Column(
        modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.grade),
                        color = 0.n1 withNight 100.n1,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Row(
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(100.n1 withNight 30.n1)
                    ) {
                        IconButton(
                            onClick = {
                                behaviorReporter.organic(AppActionId.MANUAL_REFRESH_GRADE)
                                gradeViewModel.refreshGrade()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新成绩",
                                tint = 0.n1 withNight 100.n1
                            )
                        }

                        IconButton(
                            onClick = {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) searchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = if (searchExpanded)
                                    Icons.Default.Close
                                else
                                    Icons.Default.Search,
                                contentDescription = null,
                                tint = 0.n1 withNight 100.n1
                            )
                        }
                    }
                }

                if (searchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = ContinuousCapsule,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = 0.n1 withNight 100.n1,
                            unfocusedTextColor = 0.n1 withNight 100.n1,
                            cursorColor = 90.a1 withNight 90.a1,
                        ),
                        placeholder = {
                            Text(
                                text = "搜索课程",
                                color = 50.n1 withNight 70.n1
                            )
                        }
                    )
                }
            }

            // Profile selector - shown when student has multiple profiles (micro-major/minor)
            if (!searchExpanded && gradeViewModel.studentProfiles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gradeViewModel.studentProfiles.forEachIndexed { index, profile ->
                        FilterChip(
                            selected = gradeViewModel.selectedProfileIndex == index,
                            onClick = { gradeViewModel.selectProfile(index) },
                            label = {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = 80.a1 withNight 50.a1,
                                selectedLabelColor = 100.n1 withNight 0.n1,
                                containerColor = 90.n1 withNight 20.n1,
                                labelColor = 10.n1 withNight 90.n1
                            ),
                            shape = ContinuousCapsule
                        )
                    }
                }
            }

            // 改成学期下拉选择（替代原来的学年+学期双筛选）
            if (!searchExpanded) {
                gradeViewModel.presetCandidates.firstOrNull()?.let { candidate ->
                    LaunchedEffect(candidate.opportunityId, candidate.presetId) {
                        gradeViewModel.onPresetCandidateVisible(candidate)
                    }
                    Text(
                        text = "使用常用条件",
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(ContinuousCapsule)
                            .background(90.a1)
                            .clickable { gradeViewModel.applyPresetCandidate(candidate) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = 0.n1,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                val allTerms = gradeViewModel.grade?.termGradeList
                    ?.sortedWith(
                        compareByDescending<Grade.TermGradeListBean> {
                            // 提取学年起始值，例如 "2023-2024" -> 2023
                            it.schoolYear.substringBefore("-").toIntOrNull() ?: 0
                        }.thenByDescending {
                            it.term.toIntOrNull() ?: 0
                        }
                    )
                    .orEmpty()
                val selectedTermText =
                    "${gradeViewModel.schoolYear} 第${gradeViewModel.schoolTerm}学期"

                ExposedDropdownMenuBox(
                    expanded = termMenuExpanded,
                    onExpandedChange = {
                        termMenuExpanded = !termMenuExpanded
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = selectedTermText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = ContinuousCapsule,
                        label = { Text("选择学期") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = 10.n1 withNight 90.n1,
                            unfocusedTextColor = 10.n1 withNight 90.n1,
                            focusedLabelColor = 40.a1 withNight 80.a1,
                            unfocusedLabelColor = 50.n1 withNight 70.n1,
                            focusedBorderColor = 40.a1 withNight 80.a1,
                            unfocusedBorderColor = 70.n1 withNight 50.n1,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = 40.a1 withNight 80.a1
                        ),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = termMenuExpanded
                            )
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = termMenuExpanded,
                        onDismissRequest = {
                            termMenuExpanded = false
                        },
                        modifier = Modifier.background(99.n1 withNight 10.n1)
                    ) {
                        allTerms.forEach { term ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${term.schoolYear} 第${term.term}学期",
                                        color = 10.n1 withNight 90.n1
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = 10.n1 withNight 90.n1
                                ),
                                onClick = {
                                    gradeViewModel.selectTerm(term.schoolYear, term.term)
                                    termMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (!searchExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Per-profile empty state
                    val rankMsg = gradeViewModel.rankEmptyMessage
                    if (gpaRankInfo == null && !rankMsg.isNullOrBlank()) {
                        Text(
                            text = rankMsg,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = 50.n1 withNight 70.n1
                        )
                    }

                    val infoList = listOf(
                        "本学期平均绩点" to gradeViewModel.termGradePointAverage,
                        "全程平均绩点" to gradeViewModel.totalGradePointAverage,
                        "全程专业排名" to ((gpaRankInfo?.majorRank ?: "暂无").toString() + "/" + (gpaRankInfo?.majorHeadCount ?: "暂无")),
                        "该学期专业排名" to ((currentRank?.majorRank ?: "暂无").toString() + "/" + (gpaRankInfo?.majorHeadCount ?: "暂无")),
                        "最后更新时间" to (gpaRankInfo?.updatedDateTimeStr ?: "暂无")
                    )

                    infoList.forEach { (title, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = 0.n1 withNight 100.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = value,
                                color = 0.n1 withNight 100.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            if (searchExpanded && trimmedQuery.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    searchResultsByTerm.forEach { (term, items) ->
                        Text(
                            text = "${term.schoolYear} 第${term.term}学期",
                            color = 0.n1 withNight 100.n1,
                            style = MaterialTheme.typography.titleMedium
                        )

                        items.forEach { item ->
                            GradeCard(
                                item = item,
                                onNavigateToEvaluation = onNavigateToEvaluation
                            )
                        }
                    }
                }
            } else if (!searchExpanded && gradeData != null && gradeData.gradeList.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    gradeData.gradeList.forEach {
                        GradeCard(
                            item = it,
                            onNavigateToEvaluation = onNavigateToEvaluation
                        )
                    }
                }
            } else if (!searchExpanded) {
                // Show empty message specific to selected profile
                val emptyMsg = if (gradeViewModel.studentProfiles.size > 1) {
                    val p = gradeViewModel.studentProfiles.getOrNull(gradeViewModel.selectedProfileIndex)
                    if (p != null) "「${p.displayName}」暂无成绩" else "该学期目前没有任何成绩"
                } else {
                    "该学期目前没有任何成绩"
                }
                Text(
                    text = emptyMsg,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = 50.n1 withNight 70.n1
                )
            }
        }
    }
    }
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
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_filter),
                    contentDescription = "选择学期：$selectedTermText",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(99.n1 withNight 10.n1)
        ) {
            allTerms.forEach { term ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${term.schoolYear} 第${term.term}学期",
                            color = 10.n1 withNight 90.n1
                        )
                    },
                    colors = MenuDefaults.itemColors(textColor = 10.n1 withNight 90.n1),
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

    val gradeCardShape = if (isRadiantUi) SmoothRoundedCornerShape(16.dp) else SmoothRoundedCornerShape(4.dp)
    val gradeCardPadding = if (isRadiantUi) PaddingValues(20.dp, 16.dp) else PaddingValues(24.dp, 16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(gradeCardShape)
            .background(100.n1 withNight 20.n1)
            .padding(gradeCardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.course ?: "",
            color = 0.n1 withNight 100.n1,
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
                modifier = Modifier.clickable(onClick = onNavigateToEvaluation),
                color = 30.n1 withNight 90.n1,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = "成绩: $gradeText    绩点: ${item.gradePoint}    学分: ${item.credit}",
                color = 30.n1 withNight 90.n1,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Text(
            text = "${item.courseNature ?: ""} (${item.courseNum ?: ""})",
            color = 50.n1 withNight 80.n1,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!needsEvaluation && !gradeDetail.isNullOrBlank()) {
            Text(
                text = gradeDetail,
                color = 40.a1 withNight 80.a1,
                style = MaterialTheme.typography.bodySmall
            )
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
