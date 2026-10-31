package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.haptics.hapticSelectable as selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Person
import com.ahu.ahutong.ui.haptics.HapticAlertDialog as AlertDialog
import com.ahu.ahutong.ui.haptics.HapticButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.ahu.ahutong.ui.haptics.HapticIconButton as IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.ahu.ahutong.ui.haptics.HapticOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.ahu.ahutong.ui.haptics.HapticRadioButton as RadioButton
import androidx.compose.material3.Surface
import com.ahu.ahutong.ui.haptics.HapticSwitch as Switch
import androidx.compose.material3.Text
import com.ahu.ahutong.ui.haptics.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.EvalTeacher
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.EvaluationViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import com.ahu.ahutong.core.designsystem.R as DesignSystemR

@Composable
fun Evaluation(
    viewModel: EvaluationViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        viewModel.loadSemesters()
    }

    val context = LocalContext.current
    val errorMessage by viewModel.errorMessage.collectAsState()
    val presetActionMessage by viewModel.presetActionMessage.collectAsState()
    val currentTask by viewModel.currentTask.collectAsState()

    BackHandler(enabled = currentTask != null) {
        viewModel.backToList()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(presetActionMessage) {
        presetActionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.presetActionMessage.value = null
        }
    }

    if (currentTask != null) {
        EvaluationFormScreen(viewModel)
    } else {
        EvaluationListScreen(viewModel, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationListScreen(
    viewModel: EvaluationViewModel,
    onBack: (() -> Unit)?
) {
    val semesters by viewModel.semesters.collectAsState()
    val selectedSemesterId by viewModel.selectedSemesterId.collectAsState()
    val taskItems by viewModel.taskItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isBulkSubmitting by viewModel.isBulkSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var presetDialogShown by remember { mutableStateOf(false) }
    var confirmBulkSubmitShown by remember { mutableStateOf(false) }
    var semesterExpanded by remember { mutableStateOf(false) }
    val presetTargetCount = remember(taskItems) {
        taskItems.sumOf { item ->
            item.taskList.sumOf { task ->
                if (!task.timeStatus) {
                    0
                } else {
                    task.teachers.count { teacher -> teacher.status == "TO_REVIEW" }
                }
            }
        }
    }
    val hasPresetTargets = presetTargetCount > 0

    if (presetDialogShown) {
        EvaluationPresetDialog(
            viewModel = viewModel,
            onDismiss = { presetDialogShown = false }
        )
    }
    if (confirmBulkSubmitShown) {
        AppDialog(
            title = "确认批量评教",
            onDismiss = { confirmBulkSubmitShown = false },
            actions = listOf(
                AppDialogAction("取消", onClick = { confirmBulkSubmitShown = false }),
                AppDialogAction(
                    "确认提交",
                    onClick = {
                        confirmBulkSubmitShown = false
                        viewModel.submitAllWithPreset()
                    },
                    style = AppDialogActionStyle.Primary
                )
            ),
            content = {
                Text(
                    text = "将按当前预设提交 $presetTargetCount 项评教，提交后通常不能撤回。",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        )
    }

    val pageContent: LazyListScope.() -> Unit = {
        item(key = "bulk-submit") {
            AppButton(
                onClick = { confirmBulkSubmitShown = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = hasPresetTargets && !isLoading && !isSubmitting && !isBulkSubmitting,
                variant = AppButtonVariant.Secondary
            ) {
                if (isBulkSubmitting) {
                    AppCircularProgressIndicator(
                        size = 16.dp,
                        strokeWidth = 2.dp,
                        color = 40.a1 withNight 80.a1
                    )
                } else {
                    Text("按预设完成全部")
                }
            }
        }

        if (isLoading && taskItems.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppCircularProgressIndicator()
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item(key = "error") {
                AppStateCard.InlineError(
                    message = errorMessage.orEmpty(),
                    title = null,
                    onRetry = {
                        if (semesters.isEmpty()) viewModel.loadSemesters()
                        else viewModel.loadEvaluationList()
                    }
                )
            }
        }

        taskItems.forEachIndexed { itemIndex, taskItem ->
            taskItem.taskList.forEachIndexed { taskIndex, task ->
                task.teachers.forEachIndexed { teacherIndex, teacher ->
                    item(
                        key = "${taskItem.lessonId}:${task.stdSumTaskId}:${teacher.teacherId}:$itemIndex:$taskIndex:$teacherIndex"
                    ) {
                        EvaluationCard(
                            task = task,
                            teacher = teacher,
                            courseName = taskItem.courseName,
                            lessonName = taskItem.lessonNameZh,
                            onClick = {
                                viewModel.enterEvaluation(
                                    task = task,
                                    teacher = teacher,
                                    courseName = taskItem.courseName,
                                    lessonName = taskItem.lessonNameZh
                                )
                            },
                            onPresetClick = {
                                viewModel.quickSubmitWithPreset(
                                    task = task,
                                    teacher = teacher,
                                    courseName = taskItem.courseName,
                                    lessonName = taskItem.lessonNameZh
                                )
                            },
                            presetEnabled = !isSubmitting && !isBulkSubmitting
                        )
                    }
                }
            }
        }

        if (!isLoading && taskItems.isEmpty() && errorMessage.isNullOrBlank()) {
            item(key = "empty") {
                AppStateCard.Empty(message = "暂无待评教课程")
            }
        }
    }

    AppPageScaffold(
        title = "评教",
        onBack = onBack,
        subtitle = semesters.firstOrNull { it.id == selectedSemesterId }?.nameZh,
        modifier = Modifier.fillMaxSize(),
        trailingContent = {
            Box {
                AppTitleIconButton(
                    icon = DesignSystemR.drawable.ic_filter,
                    contentDescription = "选择学期",
                    onClick = { semesterExpanded = true }
                )
                DropdownMenu(
                    expanded = semesterExpanded,
                    onDismissRequest = { semesterExpanded = false },
                    containerColor = 100.n1 withNight 20.n1
                ) {
                    semesters.forEach { semester ->
                        DropdownMenuItem(
                            text = { Text(semester.nameZh) },
                            onClick = {
                                viewModel.selectedSemesterId.value = semester.id
                                viewModel.loadEvaluationList()
                                semesterExpanded = false
                            },
                            leadingIcon = if (semester.id == selectedSemesterId) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = 40.a1 withNight 80.a1
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_config,
                contentDescription = "评教预设",
                onClick = { presetDialogShown = true }
            )
        },
        bottomPadding = 48.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        lazyContent = pageContent
    )
}

@Composable
private fun EvaluationCard(
    task: EvalTask,
    teacher: EvalTeacher,
    courseName: String,
    lessonName: String,
    onClick: () -> Unit,
    onPresetClick: () -> Unit,
    presetEnabled: Boolean
) {
    val reviewed = teacher.status != "TO_REVIEW"

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = SmoothRoundedCornerShape(16.dp),
        enabled = !reviewed && task.timeStatus,
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = courseName,
                    modifier = Modifier.weight(1f),
                    color = 0.n1 withNight 100.n1,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                val (statusText, statusColor) = when {
                    reviewed -> "已评" to (50.n1 withNight 70.n1)
                    !task.timeStatus -> "未开始" to Color(0xFF2E7D32)
                    else -> "待评" to (90.a1 withNight 90.a1)
                }
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = 50.n1 withNight 70.n1
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = teacher.teacherName,
                    color = 30.n1 withNight 90.n1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "$lessonName · ${task.evaluationQuestionnaireName}",
                color = 50.n1 withNight 80.n1,
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AppButton(
                    onClick = onPresetClick,
                    enabled = !reviewed && task.timeStatus && presetEnabled,
                    variant = AppButtonVariant.Secondary
                ) {
                    Text(
                        text = "按预设完成",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationFormScreen(viewModel: EvaluationViewModel) {
    val questions by viewModel.questions.collectAsState()
    val currentTeacher by viewModel.currentTeacher.collectAsState()
    val currentCourseName by viewModel.currentCourseName.collectAsState()
    val currentLessonName by viewModel.currentLessonName.collectAsState()
    val answers by viewModel.answers.collectAsState()
    val textAnswers by viewModel.textAnswers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val submitSuccess by viewModel.submitSuccess.collectAsState()
    val submitMessage by viewModel.submitMessage.collectAsState()

    val context = LocalContext.current
    var presetDialogShown by remember { mutableStateOf(false) }

    if (presetDialogShown) {
        EvaluationPresetDialog(
            viewModel = viewModel,
            onDismiss = { presetDialogShown = false }
        )
    }

    LaunchedEffect(submitMessage) {
        submitMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            if (submitSuccess) {
                viewModel.backToList()
            } else {
                viewModel.submitMessage.value = null
            }
        }
    }

    val pageContent: LazyListScope.() -> Unit = {
        if (isLoading && questions.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { AppCircularProgressIndicator() }
            }
        } else {
            item(key = "preset-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppButton(
                        onClick = { viewModel.applyPresetToCurrent() },
                        modifier = Modifier.weight(1f),
                        enabled = questions.isNotEmpty() && !isSubmitting,
                        variant = AppButtonVariant.Secondary
                    ) { Text("套用预设") }
                    AppButton(
                        onClick = { viewModel.submitCurrentWithPreset() },
                        modifier = Modifier.weight(1f),
                        enabled = questions.isNotEmpty() && !isSubmitting
                    ) { Text("预设提交") }
                }
            }
            questions.forEachIndexed { index, question ->
                item(key = "question:${question.attribute.id}:$index") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        QuestionCard(
                            question = question,
                            selectedOptionId = answers[question.attribute.id.toString()],
                            textAnswer = textAnswers[question.attribute.id.toString()].orEmpty(),
                            onSelect = { optionId ->
                                viewModel.setAnswer(question.attribute.id.toString(), optionId)
                            },
                            onTextChange = { text ->
                                viewModel.setTextAnswer(question.attribute.id.toString(), text)
                            }
                        )
                    }
                }
            }
        }

        item(key = "submit-actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    onClick = { viewModel.backToList() },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.Secondary
                ) {
                    Text("取消")
                }
                AppButton(
                    onClick = { viewModel.submit(false) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && questions.isNotEmpty()
                ) {
                    if (isSubmitting) {
                        AppCircularProgressIndicator(
                            size = 16.dp,
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("提交")
                    }
                }
                AppButton(
                    onClick = { viewModel.submit(true) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && questions.isNotEmpty()
                ) {
                    Text("匿名")
                }
            }
        }
    }

    AppPageScaffold(
        title = currentCourseName.ifBlank { "课程评教" },
        onBack = { viewModel.backToList() },
        subtitle = "${currentTeacher?.teacherName.orEmpty()} · $currentLessonName",
        modifier = Modifier.fillMaxSize(),
        trailingContent = {
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_config,
                contentDescription = "评教预设",
                onClick = { presetDialogShown = true }
            )
        },
        bottomPadding = 48.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        lazyContent = pageContent
    )
}

@Composable
private fun EvaluationPresetDialog(
    viewModel: EvaluationViewModel,
    onDismiss: () -> Unit
) {
    val preset by viewModel.preset.collectAsState()
    val presetQuestions by viewModel.presetQuestions.collectAsState()
    val isPresetLoading by viewModel.isPresetLoading.collectAsState()
    val taskItems by viewModel.taskItems.collectAsState()
    var radioIndexes by remember(preset, presetQuestions) {
        mutableStateOf(viewModel.presetRadioOptionIndexesFor(presetQuestions, preset))
    }
    var presetTextAnswers by remember(preset, presetQuestions) {
        mutableStateOf(viewModel.presetTextAnswersFor(presetQuestions, preset))
    }
    var anonymous by remember(preset) {
        mutableStateOf(preset.anonymous)
    }

    LaunchedEffect(taskItems, presetQuestions.isEmpty()) {
        viewModel.loadPresetQuestions()
    }

    AppDialog(
        title = "评教预设",
        onDismiss = onDismiss,
        contentSpacing = 14.dp,
        actions = listOf(
            AppDialogAction("取消", onClick = onDismiss),
            AppDialogAction(
                "保存",
                enabled = presetQuestions.isNotEmpty(),
                style = AppDialogActionStyle.Primary,
                onClick = {
                    viewModel.savePreset(
                        radioOptionIndexes = radioIndexes,
                        textAnswers = presetTextAnswers,
                        anonymous = anonymous
                    )
                    onDismiss()
                }
            )
        ),
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "匿名提交",
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.bodyLarge
                )
                AppToggle(
                    checked = anonymous,
                    onCheckedChange = { anonymous = it },
                    contentDescription = "匿名提交"
                )
            }

            when {
                isPresetLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppCircularProgressIndicator()
                    }
                }
                presetQuestions.isEmpty() -> {
                    Text(
                        text = "暂无可编辑的评教题目",
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        presetQuestions.forEach { question ->
                            val questionId = question.attribute.id.toString()
                            PresetQuestionEditor(
                                question = question,
                                selectedOptionIndex = radioIndexes[questionId],
                                textAnswer = presetTextAnswers[questionId].orEmpty(),
                                onOptionIndexChange = { optionIndex ->
                                    radioIndexes = radioIndexes + (questionId to optionIndex)
                                },
                                onTextChange = { text ->
                                    presetTextAnswers = presetTextAnswers + (questionId to text)
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PresetQuestionEditor(
    question: EvalQuestion,
    selectedOptionIndex: Int?,
    textAnswer: String,
    onOptionIndexChange: (Int) -> Unit,
    onTextChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${question.index}. ${question.attribute.name}",
            color = 0.n1 withNight 100.n1,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )

        when (question.attribute.typeId) {
            1 -> {
                question.options.forEachIndexed { optionIndex, option ->
                    val selected = selectedOptionIndex == optionIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .selectable(selected = selected, role = Role.RadioButton,
                                onClick = { onOptionIndexChange(optionIndex) })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onOptionIndexChange(optionIndex) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = option.value,
                            modifier = Modifier.weight(1f),
                            color = 0.n1 withNight 100.n1,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (question.attribute.enableScore) {
                            Text(
                                text = "${option.optionScore}分",
                                color = 50.n1 withNight 70.n1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            4 -> {
                OutlinedTextField(
                    value = textAnswer,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = 0.n1 withNight 100.n1,
                        unfocusedTextColor = 0.n1 withNight 100.n1,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = 90.n1 withNight 30.n1,
                        cursorColor = 90.a1 withNight 90.a1
                    ),
                    shape = SmoothRoundedCornerShape(12.dp)
                )
            }
            else -> {
                Text(
                    text = "暂不支持该题型",
                    color = 50.n1 withNight 80.n1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: EvalQuestion,
    selectedOptionId: String?,
    textAnswer: String,
    onSelect: (String) -> Unit,
    onTextChange: (String) -> Unit
) {
    val attr = question.attribute

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SmoothRoundedCornerShape(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${question.index}.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = attr.name,
                    modifier = Modifier.weight(1f),
                    color = 0.n1 withNight 100.n1,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (attr.required) {
                    Text(
                        text = "*",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }

            when (attr.typeId) {
                1 -> {
                    question.options.forEach { option ->
                        val optionId = option.optionId.toString()
                        val selected = selectedOptionId == optionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SmoothRoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .selectable(selected = selected, role = Role.RadioButton,
                                    onClick = { onSelect(optionId) })
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelect(optionId) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = option.value,
                                    color = 0.n1 withNight 100.n1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (attr.enableScore) {
                                Text(
                                    text = "${option.optionScore}分",
                                    color = 50.n1 withNight 70.n1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                4 -> {
                    OutlinedTextField(
                        value = textAnswer,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "请输入评语",
                                color = 50.n1 withNight 70.n1
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = 0.n1 withNight 100.n1,
                            unfocusedTextColor = 0.n1 withNight 100.n1,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = 90.n1 withNight 30.n1,
                            focusedPlaceholderColor = 50.n1 withNight 70.n1,
                            unfocusedPlaceholderColor = 50.n1 withNight 70.n1,
                            cursorColor = 90.a1 withNight 90.a1
                        ),
                        shape = SmoothRoundedCornerShape(12.dp)
                    )
                }
                else -> {
                    Text(
                        text = "暂不支持该题型",
                        color = 50.n1 withNight 80.n1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (attr.enableScore) {
                Text(
                    text = "本题 ${attr.score} 分",
                    color = 50.n1 withNight 70.n1,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
