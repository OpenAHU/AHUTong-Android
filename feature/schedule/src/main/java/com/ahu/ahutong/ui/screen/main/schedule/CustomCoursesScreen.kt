package com.ahu.ahutong.ui.screen.main.schedule

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.schedule.CUSTOM_COURSE_MAX_WEEK
import com.ahu.ahutong.data.schedule.CustomCourse
import com.ahu.ahutong.data.schedule.CustomCourseMapper
import com.ahu.ahutong.data.schedule.CustomCourseMath
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.state.CustomCourseDraft
import com.ahu.ahutong.ui.state.CustomCourseSlotDraft
import com.ahu.ahutong.ui.state.CustomCoursesUiState
import com.ahu.ahutong.ui.state.CustomCoursesViewModel

private val CustomCoursePalette = listOf(
    0xFF82ADF7, 0xFF7AE3D2, 0xFF77B6EF, 0xFFE19BB0, 0xFFE38874, 0xFF679ACD,
    0xFFE87897, 0xFFEBB877, 0xFFC8A2C8, 0xFFA8E4A0, 0xFFFF8A80, 0xFFB39DDB
).map { it.toInt() }

@Composable
fun CustomCoursesScreen(
    initialEditId: String?,
    onBack: () -> Unit,
    viewModel: CustomCoursesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load(initialEditId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    BackHandler(enabled = state.editor != null) { viewModel.closeEditor() }

    val editor = state.editor
    if (editor == null) {
        CustomCourseListScaffold(
            state = state,
            onBack = onBack,
            onAdd = viewModel::openCreate,
            onEdit = viewModel::openEdit,
            onDelete = viewModel::requestDelete
        )
    } else {
        CustomCourseEditorScaffold(
            state = state,
            draft = editor,
            onClose = viewModel::closeEditor,
            onUpdate = viewModel::updateEditor,
            onSlotChange = viewModel::updateSlot,
            onAddSlot = viewModel::addSlot,
            onCopySlot = viewModel::copySlot,
            onRemoveSlot = viewModel::removeSlot,
            onSave = viewModel::save,
            onDelete = { viewModel.requestDelete(editor.id) }
        )
    }

    state.pendingDeleteId?.let { pendingId ->
        val course = state.courses.firstOrNull { it.id == pendingId }
        AppDialog(
            title = "删除自定义课程",
            subtitle = course?.name,
            onDismiss = viewModel::dismissDelete,
            actions = listOf(
                AppDialogAction("取消", viewModel::dismissDelete),
                AppDialogAction(
                    "删除",
                    onClick = viewModel::confirmDelete,
                    style = AppDialogActionStyle.Danger
                )
            ),
            content = {
                Text(
                    "将从本机移除这门课，不影响教务课表。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@Composable
private fun CustomCourseListScaffold(
    state: CustomCoursesUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    AppPageScaffold(
        title = "自定义课程",
        subtitle = semesterDisplayName(state.semesterKey),
        onBack = onBack,
        actions = if (state.semesterKey == null) {
            emptyList()
        } else {
            listOf(TrailingAction(Icons.Default.Add, "添加课程", onClick = onAdd))
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            when {
                state.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppCircularProgressIndicator()
                }
                state.semesterKey == null -> AppSectionCard {
                    Text(
                        "还没有确定当前学期，请先登录并打开一次课表后再添加。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.courses.isEmpty() -> AppSectionCard {
                    Text(
                        "还没有自定义课程。",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "教务课表里的课会自动同步；自己加的课、晚自习、培训等可以在这里手动添加。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> state.courses.forEach { course ->
                    CustomCourseListItem(
                        course = course,
                        onEdit = { onEdit(course.id) },
                        onDelete = { onDelete(course.id) }
                    )
                }
            }
            if (state.semesterKey != null) {
                AppButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("添加课程")
                }
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
private fun CustomCourseListItem(
    course: CustomCourse,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppSectionCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        course.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                    )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = course.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = course.slotSummary(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                course.note?.let { note ->
                    Text(
                        text = "备注：$note",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

private fun semesterDisplayName(semesterKey: String?): String? {
    if (semesterKey.isNullOrBlank()) return null
    val parts = semesterKey.split("-")
    return if (parts.size >= 3) {
        "${parts[0]}-${parts[1]} 学年第${parts[2]}学期"
    } else {
        semesterKey
    }
}

private fun CustomCourse.slotSummary(): String {
    val mapped = CustomCourseMapper.toCourses(this)
    if (mapped.isEmpty()) return "时间待完善"
    return mapped.joinToString("；") { course ->
        val lastSection = course.startTime + course.length - 1
        val sections = if (course.startTime == lastSection) {
            "第${course.startTime}节"
        } else {
            "第${course.startTime}-${lastSection}节"
        }
        val clock = course.clockRange?.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        "${scheduleWeekdayLabel(course.weekday)} $sections$clock ${course.weekRangeText()}"
    }
}

@Composable
private fun CustomCourseEditorScaffold(
    state: CustomCoursesUiState,
    draft: CustomCourseDraft,
    onClose: () -> Unit,
    onUpdate: ((CustomCourseDraft) -> CustomCourseDraft) -> Unit,
    onSlotChange: (Int, (CustomCourseSlotDraft) -> CustomCourseSlotDraft) -> Unit,
    onAddSlot: () -> Unit,
    onCopySlot: (Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    AppPageScaffold(
        title = if (draft.isNew) "添加课程" else "编辑课程",
        subtitle = semesterDisplayName(state.semesterKey),
        onBack = onClose,
        trailingContent = {
            AppTitleIconButton(
                onClick = onSave,
                contentDescription = "保存",
                imageVector = Icons.Default.Check,
                enabled = !state.saving,
                loading = state.saving
            )
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            AppSectionCard {
                AppTextField(
                    value = draft.name,
                    onValueChange = { value -> onUpdate { it.copy(name = value.take(40)) } },
                    label = "课程名称",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "颜色",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                CourseColorRow(
                    selected = draft.colorArgb,
                    onSelect = { color -> onUpdate { it.copy(colorArgb = color) } }
                )
            }

            draft.slots.forEachIndexed { index, slot ->
                CustomCourseSlotEditor(
                    index = index,
                    slotCount = draft.slots.size,
                    slot = slot,
                    onChange = { updated ->
                        onSlotChange(index) { updated }
                    },
                    onCopy = { onCopySlot(index) },
                    onRemove = { onRemoveSlot(index) }
                )
            }

            AppButton(
                onClick = onAddSlot,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Secondary
            ) {
                Text("添加上课时间")
            }

            AppSectionCard {
                AppTextField(
                    value = draft.teacher,
                    onValueChange = { value -> onUpdate { it.copy(teacher = value.take(30)) } },
                    label = "教师（可选）",
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = draft.location,
                    onValueChange = { value -> onUpdate { it.copy(location = value.take(40)) } },
                    label = "地点（可选）",
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = draft.note,
                    onValueChange = { value -> onUpdate { it.copy(note = value.take(120)) } },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AppButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving
            ) {
                Text(if (state.saving) "正在保存…" else "保存课程")
            }
            if (!draft.isNew) {
                AppButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.Destructive
                ) {
                    Text("删除此课程")
                }
            }
        }
    )
}

@Composable
private fun CourseColorRow(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppFilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("自动") }
        )
        CustomCoursePalette.forEach { argb ->
            val isSelected = selected == argb
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(argb) }
            )
        }
    }
}

@Composable
private fun CustomCourseSlotEditor(
    index: Int,
    slotCount: Int,
    slot: CustomCourseSlotDraft,
    onChange: (CustomCourseSlotDraft) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val span = slot.resolvedSpan()
    AppSectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "上课时间 ${index + 1}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Row {
                TextButton(onClick = onCopy) { Text("复制") }
                if (slotCount > 1) {
                    TextButton(onClick = onRemove) { Text("删除") }
                }
            }
        }

        AppSelectField(
            label = "星期",
            selected = slot.weekday,
            options = (1..7).map { AppSelectOption(it, scheduleWeekdayLabel(it)) },
            onSelected = { weekday -> onChange(slot.copy(weekday = weekday)) },
            modifier = Modifier.fillMaxWidth()
        )

        WeekPicker(
            selected = slot.weeks,
            onToggle = { week ->
                val weeks = if (week in slot.weeks) slot.weeks - week else slot.weeks + week
                onChange(slot.copy(weeks = weeks))
            },
            onReplace = { weeks -> onChange(slot.copy(weeks = weeks)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用自定义时间",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "适用于时间不固定的课；课表按最近节次吸附显示",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppToggle(
                checked = slot.useCustomTime,
                onCheckedChange = { enabled ->
                    val snapped = CustomCourseMath.snapToSections(
                        slot.startMinutes,
                        slot.endMinutes
                    )
                    onChange(
                        slot.copy(
                            useCustomTime = enabled,
                            startSection = snapped.startSection,
                            endSection = snapped.endSection
                        )
                    )
                },
                contentDescription = "使用自定义时间"
            )
        }

        if (slot.useCustomTime) {
            TimeRow(
                label = "开始时间",
                minutes = slot.startMinutes,
                onPick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        onChange(slot.copy(startMinutes = hour * 60 + minute))
                    }, slot.startMinutes / 60, slot.startMinutes % 60, true).show()
                }
            )
            TimeRow(
                label = "结束时间",
                minutes = slot.endMinutes,
                onPick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        onChange(slot.copy(endMinutes = hour * 60 + minute))
                    }, slot.endMinutes / 60, slot.endMinutes % 60, true).show()
                }
            )
            Text(
                text = "将显示在第 ${span.startSection}-${span.endSection} 节",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        } else {
            AppSelectField(
                label = "开始节次",
                selected = slot.startSection,
                options = (1..CustomCourseMath.MAX_SECTION).map {
                    AppSelectOption(it, "第${it}节")
                },
                onSelected = { section ->
                    onChange(
                        slot.copy(
                            startSection = section,
                            endSection = slot.endSection.coerceAtLeast(section)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            AppSelectField(
                label = "结束节次",
                selected = slot.endSection,
                options = (slot.startSection..CustomCourseMath.MAX_SECTION).map {
                    AppSelectOption(it, "第${it}节")
                },
                onSelected = { section -> onChange(slot.copy(endSection = section)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeekPicker(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onReplace: (Set<Int>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "上课周次（已选 ${selected.size} 周）",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                TextButton(onClick = { onReplace((1..CUSTOM_COURSE_MAX_WEEK).toSet()) }) {
                    Text("全选")
                }
                TextButton(onClick = {
                    onReplace((1..CUSTOM_COURSE_MAX_WEEK).filter { it % 2 == 1 }.toSet())
                }) { Text("单周") }
                TextButton(onClick = {
                    onReplace((1..CUSTOM_COURSE_MAX_WEEK).filter { it % 2 == 0 }.toSet())
                }) { Text("双周") }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..CUSTOM_COURSE_MAX_WEEK).forEach { week ->
                AppFilterChip(
                    selected = week in selected,
                    onClick = { onToggle(week) },
                    label = { Text("$week") }
                )
            }
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    minutes: Int,
    onPick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = CustomCourseMath.formatClockMinutes(minutes),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
