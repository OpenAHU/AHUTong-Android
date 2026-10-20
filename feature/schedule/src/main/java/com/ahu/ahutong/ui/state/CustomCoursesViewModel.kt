package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.data.schedule.CUSTOM_COURSE_MAX_WEEK
import com.ahu.ahutong.data.schedule.CustomCourse
import com.ahu.ahutong.data.schedule.CustomCourseMath
import com.ahu.ahutong.data.schedule.CustomCourseSlot
import com.ahu.ahutong.data.schedule.CustomCourseStore
import com.ahu.ahutong.data.schedule.SectionSpan
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class CustomCoursesUiState(
    val loading: Boolean = true,
    val semesterKey: String? = null,
    val courses: List<CustomCourse> = emptyList(),
    val editor: CustomCourseDraft? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingDeleteId: String? = null
)

data class CustomCourseDraft(
    val id: String,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val note: String = "",
    val colorArgb: Int? = null,
    val slots: List<CustomCourseSlotDraft> = listOf(CustomCourseSlotDraft()),
    val isNew: Boolean = true
)

data class CustomCourseSlotDraft(
    val weekday: Int = 1,
    val weeks: Set<Int> = (1..CUSTOM_COURSE_MAX_WEEK).toSet(),
    val startSection: Int = 1,
    val endSection: Int = 1,
    val useCustomTime: Boolean = false,
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 8 * 60 + 45
) {
    fun resolvedSpan(): SectionSpan =
        if (useCustomTime) {
            CustomCourseMath.snapToSections(startMinutes, endMinutes)
        } else {
            val first = startSection.coerceIn(1, CustomCourseMath.MAX_SECTION)
            SectionSpan(first, endSection.coerceIn(first, CustomCourseMath.MAX_SECTION))
        }
}

@HiltViewModel
class CustomCoursesViewModel @Inject constructor(
    private val store: CustomCourseStore,
    private val reminders: CourseReminderControl
) : ViewModel() {

    private val _state = MutableStateFlow(CustomCoursesUiState())
    val state: StateFlow<CustomCoursesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var initialEditHandled = false

    fun load(initialEditId: String?) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launchSafe {
            val (semesterKey, courses) = withContext(Dispatchers.IO) {
                store.currentSemesterKey() to store.currentCourses()
            }
            _state.update {
                it.copy(loading = false, semesterKey = semesterKey, courses = courses)
            }
            if (!initialEditHandled) {
                initialEditHandled = true
                initialEditId?.let(::openEdit)
            }
        }
    }

    fun openCreate() {
        _state.update {
            it.copy(
                editor = CustomCourseDraft(id = UUID.randomUUID().toString()),
                error = null
            )
        }
    }

    fun openEdit(id: String) {
        val course = _state.value.courses.firstOrNull { it.id == id } ?: return
        val slots = course.slots.map { slot ->
            val span = slot.resolvedSpan() ?: SectionSpan(1, 1)
            CustomCourseSlotDraft(
                weekday = slot.weekday,
                weeks = slot.weekIndexes.toSet(),
                startSection = span.startSection,
                endSection = span.endSection,
                useCustomTime = slot.hasCustomTime,
                startMinutes = slot.customStartMinutes
                    ?: CustomCourseMath.sectionStartMinutes(span.startSection) ?: 8 * 60,
                endMinutes = slot.customEndMinutes
                    ?: CustomCourseMath.sectionEndMinutes(span.endSection) ?: 8 * 60 + 45
            )
        }
        _state.update {
            it.copy(
                editor = CustomCourseDraft(
                    id = course.id,
                    name = course.name,
                    teacher = course.teacher.orEmpty(),
                    location = course.location.orEmpty(),
                    note = course.note.orEmpty(),
                    colorArgb = course.colorArgb,
                    slots = slots.ifEmpty { listOf(CustomCourseSlotDraft()) },
                    isNew = false
                ),
                error = null
            )
        }
    }

    fun closeEditor() {
        _state.update { it.copy(editor = null, error = null) }
    }

    fun updateEditor(transform: (CustomCourseDraft) -> CustomCourseDraft) {
        _state.update { current ->
            current.editor?.let { current.copy(editor = transform(it), error = null) } ?: current
        }
    }

    fun updateSlot(index: Int, transform: (CustomCourseSlotDraft) -> CustomCourseSlotDraft) {
        updateEditor { draft ->
            if (index !in draft.slots.indices) return@updateEditor draft
            draft.copy(
                slots = draft.slots.mapIndexed { slotIndex, slot ->
                    if (slotIndex == index) transform(slot) else slot
                }
            )
        }
    }

    fun addSlot() {
        updateEditor { draft ->
            val last = draft.slots.lastOrNull()
            val slot = last?.copy(weeks = last.weeks.toSet()) ?: CustomCourseSlotDraft()
            draft.copy(slots = draft.slots + slot)
        }
    }

    fun copySlot(index: Int) {
        updateEditor { draft ->
            val source = draft.slots.getOrNull(index) ?: return@updateEditor draft
            draft.copy(slots = draft.slots.toMutableList().apply { add(index + 1, source.copy()) })
        }
    }

    fun removeSlot(index: Int) {
        updateEditor { draft ->
            if (draft.slots.size <= 1) return@updateEditor draft
            draft.copy(slots = draft.slots.filterIndexed { slotIndex, _ -> slotIndex != index })
        }
    }

    fun save() {
        val draft = _state.value.editor ?: return
        validate(draft)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launchSafe {
            val built = draft.toCustomCourse()
            val courses = _state.value.courses.toMutableList().apply {
                val index = indexOfFirst { it.id == built.id }
                if (index >= 0) set(index, built) else add(built)
            }
            runCatching { store.saveCurrent(courses) }
                .onSuccess {
                    runCatching { reminders.reschedule() }
                    _state.update {
                        it.copy(
                            saving = false,
                            courses = courses,
                            editor = null,
                            message = "自定义课程已保存",
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = error.message ?: "保存失败，请稍后重试"
                        )
                    }
                }
        }
    }

    fun requestDelete(id: String) {
        _state.update { it.copy(pendingDeleteId = id) }
    }

    fun dismissDelete() {
        _state.update { it.copy(pendingDeleteId = null) }
    }

    fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        val remaining = _state.value.courses.filterNot { it.id == id }
        viewModelScope.launchSafe {
            runCatching { store.saveCurrent(remaining) }
                .onSuccess {
                    runCatching { reminders.reschedule() }
                    _state.update {
                        it.copy(
                            courses = remaining,
                            pendingDeleteId = null,
                            editor = if (it.editor?.id == id) null else it.editor,
                            message = "已删除自定义课程",
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            pendingDeleteId = null,
                            error = error.message ?: "删除失败，请稍后重试"
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun validate(draft: CustomCourseDraft): String? {
        if (draft.name.isBlank()) return "请填写课程名称"
        if (draft.slots.isEmpty()) return "至少需要一个上课时间"
        draft.slots.forEachIndexed { index, slot ->
            val label = "第${index + 1}个上课时间"
            if (slot.weeks.isEmpty()) return "请为${label}选择周次"
            if (slot.weekday !in 1..7) return "${label}的星期不正确"
            if (slot.useCustomTime) {
                if (slot.startMinutes !in 0..(24 * 60 - 1) ||
                    slot.endMinutes !in 0..(24 * 60 - 1)
                ) {
                    return "${label}的时间不正确"
                }
                if (slot.endMinutes <= slot.startMinutes) return "${label}的结束时间应晚于开始时间"
            } else if (slot.endSection < slot.startSection) {
                return "${label}的结束节次不能早于开始节次"
            }
        }
        return null
    }

    private fun CustomCourseDraft.toCustomCourse(): CustomCourse = CustomCourse(
        id = id,
        name = name.trim(),
        teacher = teacher.trim().takeIf(String::isNotBlank),
        location = location.trim().takeIf(String::isNotBlank),
        note = note.trim().takeIf(String::isNotBlank),
        colorArgb = colorArgb,
        slots = slots.mapNotNull { slot ->
            val weeks = slot.weeks
                .filter { it in 1..CUSTOM_COURSE_MAX_WEEK }
                .distinct()
                .sorted()
            if (weeks.isEmpty()) return@mapNotNull null
            val span = slot.resolvedSpan()
            CustomCourseSlot(
                weekday = slot.weekday,
                weekIndexes = weeks,
                startSection = span.startSection,
                endSection = span.endSection,
                customStartMinutes = slot.startMinutes.takeIf { slot.useCustomTime },
                customEndMinutes = slot.endMinutes.takeIf { slot.useCustomTime }
            )
        }
    )
}
