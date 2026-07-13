package com.ahu.ahutong.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.feature.grade.R
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.components.AhuChip
import com.ahu.ahutong.ui.components.AhuChipRow
import com.ahu.ahutong.ui.components.AhuErrorToastEffect
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuIconActionGroup
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.state.GradeViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Grade(
    gradeViewModel: GradeViewModel = hiltViewModel(),
    mockRefreshRevision: Long = 0L,
) {
    val grade = gradeViewModel.grade
    val gpaRankInfo = gradeViewModel.gpaRankInfo
    val errorMessage = gradeViewModel.errorMessage

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
        if (mockRefreshRevision > 0 && gradeViewModel.isMockMode()) {
            gradeViewModel.getGarde(isRefresh = true)
            gradeViewModel.getGpaRank()
        }
    }

    AhuErrorToastEffect(errorMessage) {
        gradeViewModel.errorMessage = null
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

    val notAvailable = stringResource(R.string.not_available)

    AhuScreen {
        AhuPageHeader(
            title = stringResource(id = R.string.grade),
            actions = {
                AhuIconActionGroup {
                    AhuHeaderIconButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_grades),
                        onClick = { gradeViewModel.refreshGrade() },
                    )
                    AhuHeaderIconButton(
                        imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = null,
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        },
                    )
                }
            },
            below = {
                if (searchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = ContinuousCapsule,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AhuColors.onSurface,
                            unfocusedTextColor = AhuColors.onSurface,
                            cursorColor = 90.a1 withNight 90.a1,
                        ),
                        placeholder = {
                            Text(stringResource(R.string.search_course))
                        }
                    )
                }
            },
        )

        // Profile selector - shown when student has multiple profiles (micro-major/minor)
        if (!searchExpanded && gradeViewModel.studentProfiles.size > 1) {
            AhuChipRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AhuDimens.ContentHorizontal),
            ) {
                gradeViewModel.studentProfiles.forEachIndexed { index, profile ->
                    AhuChip(
                        text = profile.displayName,
                        selected = gradeViewModel.selectedProfileIndex == index,
                        onClick = { gradeViewModel.selectedProfileIndex = index },
                    )
                }
            }
        }

        // 改成学期下拉选择（替代原来的学年+学期双筛选）
        if (!searchExpanded) {
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
            val selectedTermText = stringResource(
                R.string.term_label,
                gradeViewModel.schoolYear.orEmpty(),
                gradeViewModel.schoolTerm.orEmpty(),
            )

            ExposedDropdownMenuBox(
                expanded = termMenuExpanded,
                onExpandedChange = {
                    termMenuExpanded = !termMenuExpanded
                },
                modifier = Modifier.padding(horizontal = AhuDimens.ContentHorizontal)
            ) {
                OutlinedTextField(
                    value = selectedTermText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = ContinuousCapsule,
                    label = { Text(stringResource(R.string.select_term)) },
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
                    modifier = Modifier.background(AhuColors.pageBackground)
                ) {
                    allTerms.forEach { term ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(
                                        R.string.term_label,
                                        term.schoolYear,
                                        term.term,
                                    ),
                                    color = AhuColors.onSurface
                                )
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = AhuColors.onSurface
                            ),
                            onClick = {
                                gradeViewModel.schoolYear = term.schoolYear
                                gradeViewModel.schoolTerm = term.term
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
                        modifier = Modifier.padding(horizontal = AhuDimens.TitleHorizontal),
                        style = MaterialTheme.typography.titleMedium,
                        color = AhuColors.onSurface.copy(alpha = 0.6f)
                    )
                }

                val infoList = listOf(
                    stringResource(R.string.term_gpa) to gradeViewModel.termGradePointAverage,
                    stringResource(R.string.total_gpa) to gradeViewModel.totalGradePointAverage,
                    stringResource(R.string.total_major_rank) to stringResource(
                        R.string.rank_format,
                        (gpaRankInfo?.majorRank ?: notAvailable).toString(),
                        (gpaRankInfo?.majorHeadCount ?: notAvailable).toString(),
                    ),
                    stringResource(R.string.term_major_rank) to stringResource(
                        R.string.rank_format,
                        (currentRank?.majorRank ?: notAvailable).toString(),
                        (gpaRankInfo?.majorHeadCount ?: notAvailable).toString(),
                    ),
                    stringResource(R.string.last_update_time) to
                        (gpaRankInfo?.updatedDateTimeStr ?: notAvailable),
                )

                infoList.forEach { (title, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AhuDimens.TitleHorizontal),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(value, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (searchExpanded && trimmedQuery.isNotBlank()) {
            Column(
                modifier = Modifier.padding(horizontal = AhuDimens.ContentHorizontal),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                searchResultsByTerm.forEach { (term, items) ->
                    Text(
                        text = stringResource(
                            R.string.term_label,
                            term.schoolYear,
                            term.term,
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )

                    items.forEach { item ->
                        GradeCard(item)
                    }
                }
            }
        } else if (!searchExpanded && gradeData != null && gradeData.gradeList.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = AhuDimens.ContentHorizontal),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                gradeData.gradeList.forEach {
                    GradeCard(it)
                }
            }
        } else if (!searchExpanded) {
            // Show empty message specific to selected profile
            val emptyMsg = if (gradeViewModel.studentProfiles.size > 1) {
                val p = gradeViewModel.studentProfiles.getOrNull(gradeViewModel.selectedProfileIndex)
                if (p != null) {
                    stringResource(R.string.no_grades_for_profile, p.displayName)
                } else {
                    stringResource(R.string.no_grades_for_term)
                }
            } else {
                stringResource(R.string.no_grades_for_term)
            }
            Text(
                text = emptyMsg,
                modifier = Modifier.padding(AhuDimens.TitleHorizontal),
                style = MaterialTheme.typography.titleLarge,
                color = AhuColors.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun GradeCard(
    item: Grade.TermGradeListBean.GradeListBean
) {
    AhuCard(
        cornerRadius = AhuDimens.ListItemCorner,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.course ?: "",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = stringResource(
                R.string.grade_detail_line,
                item.grade.orEmpty(),
                item.gradePoint.orEmpty(),
                item.credit.orEmpty(),
            ),
            color = 30.n1 withNight 90.n1,
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "${item.courseNature ?: ""} (${item.courseNum ?: ""})",
            color = 50.n1 withNight 80.n1,
            style = MaterialTheme.typography.bodyMedium
        )

        val gradeDetail = item.gradeDetail?.replace(Regex("<[^>]*>"), "")?.trim()
        if (!gradeDetail.isNullOrBlank()) {
            Text(
                text = gradeDetail,
                color = 40.a1 withNight 80.a1,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
