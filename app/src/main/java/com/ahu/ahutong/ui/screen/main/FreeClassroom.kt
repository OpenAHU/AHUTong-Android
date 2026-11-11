package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppComponentTokens
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.FreeClassroomViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FreeClassroom(
    onBack: (() -> Unit)? = null,
    freeClassroomViewModel: FreeClassroomViewModel = hiltViewModel()
) {
    DisposableEffect(freeClassroomViewModel) {
        onDispose { freeClassroomViewModel.onPresetSurfaceDisposed() }
    }

    val campusOptions = freeClassroomViewModel.campusOptions
    val selectedCampusId by freeClassroomViewModel.selectedCampusId.collectAsState()
    val buildings by freeClassroomViewModel.buildings.collectAsState()
    val selectedBuildingIds by freeClassroomViewModel.selectedBuildingIds.collectAsState()
    val selectedUnits by freeClassroomViewModel.selectedUnits.collectAsState()
    val startDate by freeClassroomViewModel.startDate.collectAsState()
    val endDate by freeClassroomViewModel.endDate.collectAsState()
    val isLoadingBuildings by freeClassroomViewModel.isLoadingBuildings.collectAsState()
    val isSearching by freeClassroomViewModel.isSearching.collectAsState()
    val hasSearched by freeClassroomViewModel.hasSearched.collectAsState()
    val rooms by freeClassroomViewModel.freeRooms.collectAsState()
    val errorMessage by freeClassroomViewModel.errorMessage.collectAsState()
    val presetCandidates by freeClassroomViewModel.presetCandidates.collectAsState()
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    var filtersExpanded by rememberSaveable { mutableStateOf(true) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            freeClassroomViewModel.refreshMockData()
        }
    }

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

    val pageContent: LazyListScope.() -> Unit = {
        presetCandidates.firstOrNull()?.let { candidate ->
            item(key = "preset-${candidate.opportunityId}-${candidate.presetId}") {
                LaunchedEffect(candidate.opportunityId, candidate.presetId) {
                    freeClassroomViewModel.onPresetCandidateVisible(candidate)
                }
                AppButton(
                    onClick = { freeClassroomViewModel.applyPresetCandidate(candidate) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    variant = AppButtonVariant.Secondary
                ) { Text("使用常用条件") }
            }
        }

        item {
            AppSectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("查询条件", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = filterSummary(
                                selectedCampusId,
                                campusOptions.firstOrNull { it.id == selectedCampusId }?.name,
                                selectedBuildingIds.size,
                                selectedUnits.size,
                                startDate,
                                endDate
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    AppHeaderIconButton(
                        imageVector = if (filtersExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (filtersExpanded) "收起查询条件" else "展开查询条件",
                        onClick = { filtersExpanded = !filtersExpanded }
                    )
                }

                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        AppSelectField(
                            label = "校区",
                            selected = selectedCampusId,
                            options = campusOptions.map { campus ->
                                AppSelectOption(campus.id, campus.name)
                            },
                            onSelected = freeClassroomViewModel::selectCampus,
                            placeholder = "请选择校区",
                            enabled = !isSearching
                        )

                        AppSelectField(
                            label = "教学楼",
                            selected = selectedBuildingIds.singleOrNull(),
                            options = buildList<AppSelectOption<Int?>> {
                                add(AppSelectOption<Int?>(null, "全部教学楼"))
                                buildings.forEach { building ->
                                    add(AppSelectOption<Int?>(building.id, building.nameZh))
                                }
                            },
                            onSelected = freeClassroomViewModel::selectBuilding,
                            placeholder = when {
                                isLoadingBuildings -> "正在加载教学楼"
                                buildings.isEmpty() -> "当前校区暂无教学楼"
                                else -> "全部教学楼"
                            },
                            enabled = !isSearching && !isLoadingBuildings && buildings.isNotEmpty()
                        )

                        FilterGroup(
                            label = "时段",
                            trailing = {
                                Row(
                                    modifier = Modifier
                                        .clip(AppComponentTokens.ControlShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { freeClassroomViewModel.selectAllUnits() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val allDaySelected = selectedUnits.isEmpty()
                                    AnimatedVisibility(
                                        visible = allDaySelected,
                                        enter = expandHorizontally(tween(durationMillis = 150)) +
                                            fadeIn(tween(durationMillis = 150)),
                                        exit = shrinkHorizontally(tween(durationMillis = 150)) +
                                            fadeOut(tween(durationMillis = 150))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.size(4.dp))
                                        }
                                    }
                                    Text(
                                        text = "全天",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        ) {
                            UnitGrid(
                                selectedUnits = selectedUnits,
                                onToggleUnit = freeClassroomViewModel::toggleUnit
                            )
                        }

                        FilterGroup(label = "日期") {
                            ChipRow {
                                item {
                                    SelectionChip(
                                        text = "今天",
                                        selected = startDate == LocalDate.now() && endDate == startDate,
                                        onClick = {
                                            freeClassroomViewModel.setDateRange(LocalDate.now(), LocalDate.now())
                                        }
                                    )
                                }
                                item {
                                    val tomorrow = LocalDate.now().plusDays(1)
                                    SelectionChip(
                                        text = "明天",
                                        selected = startDate == tomorrow && endDate == tomorrow,
                                        onClick = { freeClassroomViewModel.setDateRange(tomorrow, tomorrow) }
                                    )
                                }
                                item {
                                    SelectionChip(
                                        text = "${startDate.monthValue}/${startDate.dayOfMonth} 起",
                                        selected = true,
                                        onClick = { showStartDatePicker = true }
                                    )
                                }
                                item {
                                    SelectionChip(
                                        text = "${endDate.monthValue}/${endDate.dayOfMonth} 止",
                                        selected = true,
                                        onClick = { showEndDatePicker = true }
                                    )
                                }
                            }
                        }
                    }
                }

                AppButton(
                    onClick = {
                        freeClassroomViewModel.searchFreeRooms()
                        filtersExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedCampusId != null && !isSearching
                ) {
                    if (isSearching) {
                        AppCircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(if (isSearching) "正在查询" else "查询空闲教室")
                }
            }
        }

        errorMessage?.let { message ->
            item {
                AppStateCard.Inline(
                    title = "查询失败",
                    message = message,
                    actionLabel = "重试",
                    actionIcon = Icons.Rounded.Refresh,
                    onAction = {
                        freeClassroomViewModel.clearError()
                        freeClassroomViewModel.searchFreeRooms()
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("查询结果", style = MaterialTheme.typography.titleLarge)
                if (hasSearched && !isSearching) {
                    Text(
                        text = "${rooms.size} 间",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        when {
            isSearching -> item { AppStateCard.Inline(title = "正在查找", message = "正在获取符合条件的教室…") }
            !hasSearched -> item {
                AppStateCard.Inline(title = "选择条件后查询", message = "默认会查询今天、全部教学楼和全天时段。")
            }
            rooms.isEmpty() && errorMessage == null -> item {
                AppStateCard.Inline(
                    title = "没有找到空闲教室",
                    message = "可以扩大日期或时段范围后再试。",
                    actionLabel = "调整条件",
                    onAction = { filtersExpanded = true }
                )
            }
            else -> items(
                items = rooms,
                key = { room -> "${room.id}-${room.building.id}" }
            ) { room ->
                FreeRoomCard(room)
            }
        }
    }

    AppPageScaffold(
        title = stringResource(id = R.string.free_classroom),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        bottomPadding = 112.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        lazyContent = pageContent
    )
}

@Composable
private fun FilterGroup(
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun ChipRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun UnitGrid(
    selectedUnits: Set<Int>,
    onToggleUnit: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..13).chunked(5).forEach { rowChoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowChoices.forEach { unit ->
                    SelectionChip(
                        text = unit.toString(),
                        selected = unit in selectedUnits,
                        onClick = { onToggleUnit(unit) },
                        modifier = Modifier.weight(1f),
                        centered = true
                    )
                }
                repeat(5 - rowChoices.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SelectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    AppFilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Box(
                modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun FreeRoomCard(room: FreeRoom) {
    AppSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(room.nameZh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = buildString {
                append(room.building.nameZh)
                append(" · ${room.floor} 层")
                room.remark?.takeIf(String::isNotBlank)?.let { append(" · $it") }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun filterSummary(
    selectedCampusId: Int?,
    campusName: String?,
    selectedBuildingCount: Int,
    selectedUnitCount: Int,
    startDate: LocalDate,
    endDate: LocalDate
): String {
    if (selectedCampusId == null) return "请选择校区"
    val building = if (selectedBuildingCount == 0) "全部教学楼" else "$selectedBuildingCount 栋教学楼"
    val units = if (selectedUnitCount == 0) "全天" else "$selectedUnitCount 个节次"
    val formatter = DateTimeFormatter.ofPattern("M月d日")
    val date = if (startDate == endDate) startDate.format(formatter)
    else "${startDate.format(formatter)}–${endDate.format(formatter)}"
    return "${campusName.orEmpty()} · $building · $units · $date"
}
