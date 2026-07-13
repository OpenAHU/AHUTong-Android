package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.feature.classroom.R
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.components.AhuChip
import com.ahu.ahutong.ui.components.AhuErrorToastEffect
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuInsetCard
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.ahu.ahutong.ui.state.FreeClassroomViewModel
import com.kyant.capsule.ContinuousCapsule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FreeClassroom(
    freeClassroomViewModel: FreeClassroomViewModel = hiltViewModel(),
    mockRefreshRevision: Long = 0L,
) {
    val campusOptions = freeClassroomViewModel.campusOptions
    val selectedCampusId by freeClassroomViewModel.selectedCampusId.collectAsState()
    val buildings by freeClassroomViewModel.buildings.collectAsState()
    val selectedBuildingIds by freeClassroomViewModel.selectedBuildingIds.collectAsState()
    val selectedUnits by freeClassroomViewModel.selectedUnits.collectAsState()
    val startDate by freeClassroomViewModel.startDate.collectAsState()
    val endDate by freeClassroomViewModel.endDate.collectAsState()
    val isLoadingBuildings by freeClassroomViewModel.isLoadingBuildings.collectAsState()
    val isSearching by freeClassroomViewModel.isSearching.collectAsState()
    val rooms by freeClassroomViewModel.freeRooms.collectAsState()
    val errorMessage by freeClassroomViewModel.errorMessage.collectAsState()
    var isFilterCollapsed by rememberSaveable { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && freeClassroomViewModel.isMockMode()) {
            freeClassroomViewModel.refreshMockData()
        }
    }

    AhuErrorToastEffect(errorMessage) {
        freeClassroomViewModel.errorMessage.value = null
    }

    AhuScreen(clearBottomNav = false) {
        AhuPageHeader(title = stringResource(id = R.string.free_classroom))

        AnimatedVisibility(
            visible = !isFilterCollapsed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AhuDimens.SectionSpacing)) {
                FilterCard(title = "选择校区") {
                    HorizontalChipRow {
                        items(campusOptions) { campus ->
                            AhuChip(
                                text = campus.name,
                                selected = selectedCampusId == campus.id,
                                onClick = { freeClassroomViewModel.selectCampus(campus.id) },
                            )
                        }
                    }
                }

                FilterCard(title = "选择教学楼") {
                    when {
                        selectedCampusId == null -> {
                            Text(
                                text = "请先选择校区",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AhuColors.onSurface.copy(alpha = 0.55f)
                            )
                        }

                        isLoadingBuildings -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = AhuColors.primaryAction
                            )
                        }

                        buildings.isEmpty() -> {
                            Text(
                                text = "当前校区暂无教学楼",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AhuColors.onSurface.copy(alpha = 0.55f)
                            )
                        }

                        else -> {
                            HorizontalChipRow {
                                items(buildings) { building ->
                                    AhuChip(
                                        text = building.nameZh,
                                        selected = building.id in selectedBuildingIds,
                                        onClick = {
                                            freeClassroomViewModel.toggleBuilding(building.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                FilterCard(
                    title = "选择节次",
                    trailingHeader = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AhuChip(
                                text = "上午",
                                selected = (1..5).all { it in selectedUnits },
                                onClick = { freeClassroomViewModel.toggleUnitsRange(1, 5) },
                            )
                            AhuChip(
                                text = "下午",
                                selected = (6..10).all { it in selectedUnits },
                                onClick = { freeClassroomViewModel.toggleUnitsRange(6, 10) },
                            )
                            AhuChip(
                                text = "晚上",
                                selected = (11..13).all { it in selectedUnits },
                                onClick = { freeClassroomViewModel.toggleUnitsRange(11, 13) },
                            )
                        }
                    }
                ) {
                    HorizontalChipRow {
                        items((1..13).toList()) { unit ->
                            AhuChip(
                                text = "${unit}节",
                                selected = unit in selectedUnits,
                                onClick = { freeClassroomViewModel.toggleUnit(unit) },
                            )
                        }
                    }
                    Text(
                        text = "未选择节次时，默认按 1-13 节查询",
                        style = MaterialTheme.typography.bodySmall,
                        color = AhuColors.onSurface.copy(alpha = 0.55f)
                    )
                }

                FilterCard(
                    title = "选择日期",
                    trailingHeader = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AhuChip(
                                text = "今天",
                                selected = startDate == LocalDate.now() && endDate == LocalDate.now(),
                                onClick = {
                                    freeClassroomViewModel.setDateRange(
                                        LocalDate.now(),
                                        LocalDate.now()
                                    )
                                },
                            )
                            AhuChip(
                                text = "明天",
                                selected = startDate == LocalDate.now().plusDays(1) &&
                                    endDate == LocalDate.now().plusDays(1),
                                onClick = {
                                    freeClassroomViewModel.setDateRange(
                                        LocalDate.now().plusDays(1),
                                        LocalDate.now().plusDays(1)
                                    )
                                },
                            )
                        }
                    }
                ) {
                    if (showStartDatePicker) {
                        MyDatePickerDialog(
                            initialDate = startDate,
                            minDate = LocalDate.now(),
                            onDateSelected = {
                                freeClassroomViewModel.setStartDate(it)
                                showStartDatePicker = false
                            },
                            onDismiss = { showStartDatePicker = false }
                        )
                    }

                    if (showEndDatePicker) {
                        MyDatePickerDialog(
                            initialDate = endDate,
                            minDate = startDate,
                            onDateSelected = {
                                freeClassroomViewModel.setEndDate(it)
                                showEndDatePicker = false
                            },
                            onDismiss = { showEndDatePicker = false }
                        )
                    }

                    HorizontalChipRow {
                        item {
                            AhuChip(
                                text = "开始: " + startDate.format(
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                ),
                                selected = true,
                                onClick = { showStartDatePicker = true },
                            )
                        }
                        item {
                            AhuChip(
                                text = "结束: " + endDate.format(
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                ),
                                selected = true,
                                onClick = { showEndDatePicker = true },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = AhuDimens.ContentHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AhuPrimaryButton(
                text = if (isSearching) "查询中..." else "开始查询空闲教室",
                onClick = {
                    freeClassroomViewModel.searchFreeRooms()
                    isFilterCollapsed = true
                },
                enabled = selectedCampusId != null && !isSearching,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )

            Box(
                modifier = Modifier
                    .clip(ContinuousCapsule)
                    .background(AhuColors.cardStrong),
            ) {
                AhuHeaderIconButton(
                    imageVector = if (isFilterCollapsed) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.KeyboardArrowUp
                    },
                    contentDescription = if (isFilterCollapsed) "展开筛选条件" else "收起筛选条件",
                    onClick = { isFilterCollapsed = !isFilterCollapsed },
                )
            }
        }

        AhuInsetCard(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "查询结果",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "共 ${rooms.size} 间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AhuColors.onSurface.copy(alpha = 0.55f)
                )
            }
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AhuColors.primaryAction
                )
            } else if (rooms.isEmpty()) {
                Text(
                    text = "暂无数据，请先设置条件后查询",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                rooms.forEach { room ->
                    AhuCard(
                        cornerRadius = 20.dp,
                        containerColor = AhuColors.cardStrong,
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = room.nameZh, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${room.building.nameZh}  ${room.floor}层  ${room.remark ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AhuColors.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalChipRow(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun FilterCard(
    title: String,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AhuInsetCard(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            trailingHeader?.invoke()
        }
        content()
    }
}
