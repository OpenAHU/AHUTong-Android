package com.ahu.ahutong.ui.screen.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
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
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.components.SecondaryPageScaffold
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.state.EvaluationViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun Evaluation(
    viewModel: EvaluationViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadSemesters()
    }

    val context = LocalContext.current
    val errorMessage by viewModel.errorMessage.collectAsState()
    val presetActionMessage by viewModel.presetActionMessage.collectAsState()
    val currentTask by viewModel.currentTask.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.errorMessage.value = null
        }
    }

    LaunchedEffect(presetActionMessage) {
        presetActionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.presetActionMessage.value = null
        }
    }

    if (isRadiantUi) {
        BackHandler(enabled = currentTask != null) { viewModel.backToList() }
    }

    if (currentTask != null) {
        EvaluationFormScreen(viewModel)
    } else {
        EvaluationListScreen(viewModel)
    }
}

@Composable
private fun EvalCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationListScreen(viewModel: EvaluationViewModel) {
    val semesters by viewModel.semesters.collectAsState()
    val selectedSemesterId by viewModel.selectedSemesterId.collectAsState()
    val taskItems by viewModel.taskItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isBulkSubmitting by viewModel.isBulkSubmitting.collectAsState()

    var semesterExpanded by remember { mutableStateOf(false) }
    var presetDialogShown by remember { mutableStateOf(false) }
    var confirmBulkSubmitShown by remember { mutableStateOf(false) }
    val presetTargetCount = taskItems.sumOf { item ->
        item.taskList.sumOf { task ->
            if (!task.timeStatus) {
                0
            } else {
                task.teachers.count { teacher -> teacher.status == "TO_REVIEW" }
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
        AlertDialog(
            onDismissRequest = { confirmBulkSubmitShown = false },
            containerColor = 100.n1 withNight 20.n1,
            titleContentColor = 0.n1 withNight 100.n1,
            textContentColor = 30.n1 withNight 90.n1,
            title = { Text("确认批量评教") },
            text = {
                Text(
                    text = "将按当前预设提交 $presetTargetCount 项评教，提交后通常不能撤回。",
                    color = 0.n1 withNight 100.n1
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBulkSubmitShown = false
                        viewModel.submitAllWithPreset()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = 40.a1 withNight 80.a1
                    )
                ) {
                    Text("确认提交")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmBulkSubmitShown = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = 40.a1 withNight 80.a1
                    )
                ) {
                    Text("取消")
                }
            }
        )
    }

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isRadiantUi) {
                Spacer(modifier = Modifier.height(72.dp))
            }
            if (!isRadiantUi) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 32.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "教评",
                modifier = Modifier.weight(1f),
                color = 0.n1 withNight 100.n1,
                style = MaterialTheme.typography.headlineMedium
            )
            Box {
                TextButton(onClick = { semesterExpanded = true }) {
                    val selected = semesters.firstOrNull { it.id == selectedSemesterId }
                    Text(
                        text = selected?.nameZh ?: "选择学期",
                        color = 40.a1 withNight 80.a1
                    )
                }
                DropdownMenu(
                    expanded = semesterExpanded,
                    onDismissRequest = { semesterExpanded = false },
                    containerColor = 100.n1 withNight 20.n1
                ) {
                    semesters.forEach { semester ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = semester.nameZh,
                                    color = 0.n1 withNight 100.n1
                                )
                            },
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
            IconButton(onClick = { presetDialogShown = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "评教预设",
                    tint = 0.n1 withNight 100.n1
                )
            }
            }
        }

        OutlinedButton(
            onClick = { confirmBulkSubmitShown = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = hasPresetTargets && !isLoading && !isSubmitting && !isBulkSubmitting,
            shape = SmoothRoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = 40.a1 withNight 80.a1,
                disabledContentColor = 60.n1 withNight 50.n1
            )
        ) {
            if (isBulkSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = 40.a1 withNight 80.a1
                )
            } else {
                Text("按预设完成全部")
            }
        }

        if (isLoading && taskItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        taskItems.forEach { item ->
            item.taskList.forEach { task ->
                task.teachers.forEach { teacher ->
                    EvaluationCard(
                        task = task,
                        teacher = teacher,
                        courseName = item.courseName,
                        lessonName = item.lessonNameZh,
                        onClick = {
                            viewModel.enterEvaluation(
                                task = task,
                                teacher = teacher,
                                courseName = item.courseName,
                                lessonName = item.lessonNameZh
                            )
                        },
                        onPresetClick = {
                            viewModel.quickSubmitWithPreset(
                                task = task,
                                teacher = teacher,
                                courseName = item.courseName,
                                lessonName = item.lessonNameZh
                            )
                        },
                        presetEnabled = !isSubmitting && !isBulkSubmitting
                    )
                }
            }
        }

        if (!isLoading && taskItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无待评教课程",
                    color = 40.n1 withNight 80.n1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    }
    if (isRadiantUi) {
        SecondaryPageScaffold(
            title = "教评",
            subtitle = semesters.firstOrNull { it.id == selectedSemesterId }?.nameZh,
            contentEdgeToEdge = true,
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        EvalCircleButton(onClick = { semesterExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = "选择学期",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = semesterExpanded,
                            onDismissRequest = { semesterExpanded = false },
                            containerColor = 100.n1 withNight 20.n1
                        ) {
                            semesters.forEach { semester ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = semester.nameZh,
                                            color = 0.n1 withNight 100.n1
                                        )
                                    },
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
                    EvalCircleButton(onClick = { presetDialogShown = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_config),
                            contentDescription = "评教预设",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        ) { body() }
    } else {
        body()
    }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(SmoothRoundedCornerShape(16.dp))
            .clickable(enabled = !reviewed && task.timeStatus, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = 100.n1 withNight 20.n1
        ),
        shape = SmoothRoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                OutlinedButton(
                    onClick = onPresetClick,
                    enabled = !reviewed && task.timeStatus && presetEnabled,
                    shape = SmoothRoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = 40.a1 withNight 80.a1,
                        disabledContentColor = 60.n1 withNight 50.n1
                    )
                ) {
                    Text(
                        text = "按预设完成",
                        color = 40.a1 withNight 80.a1,
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

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {

        if (isLoading && questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isRadiantUi) {
                    Spacer(modifier = Modifier.height(72.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.applyPresetToCurrent() },
                        modifier = Modifier.weight(1f),
                        enabled = questions.isNotEmpty() && !isSubmitting,
                        shape = SmoothRoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = 40.a1 withNight 80.a1,
                            disabledContentColor = 60.n1 withNight 50.n1
                        )
                    ) {
                        Text("套用预设")
                    }
                    Button(
                        onClick = { viewModel.submitCurrentWithPreset() },
                        modifier = Modifier.weight(1f),
                        enabled = questions.isNotEmpty() && !isSubmitting,
                        shape = SmoothRoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = 90.a1 withNight 30.a1,
                            contentColor = 100.n1 withNight 100.n1,
                            disabledContainerColor = 80.n1 withNight 25.n1,
                            disabledContentColor = 50.n1 withNight 60.n1
                        )
                    ) {
                        Text("预设提交")
                    }
                }
                questions.forEach { question ->
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
                Spacer(Modifier.height(80.dp))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = 100.n1 withNight 20.n1,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.backToList() },
                    modifier = Modifier.weight(1f),
                    shape = SmoothRoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = 40.a1 withNight 80.a1,
                        disabledContentColor = 60.n1 withNight 50.n1
                    )
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { viewModel.submit(false) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && questions.isNotEmpty(),
                    shape = SmoothRoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = 90.a1 withNight 30.a1,
                        contentColor = 100.n1 withNight 100.n1,
                        disabledContainerColor = 80.n1 withNight 25.n1,
                        disabledContentColor = 50.n1 withNight 60.n1
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("提交")
                    }
                }
                Button(
                    onClick = { viewModel.submit(true) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && questions.isNotEmpty(),
                    shape = SmoothRoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = 90.a1 withNight 30.n1,
                        contentColor = 100.n1 withNight 100.n1,
                        disabledContainerColor = 80.n1 withNight 25.n1,
                        disabledContentColor = 50.n1 withNight 60.n1
                    )
                ) {
                    Text("匿名")
                }
            }
        }
    }
    if (isRadiantUi) {
        SecondaryPageScaffold(
            title = currentCourseName,
            subtitle = "${currentTeacher?.teacherName.orEmpty()} · $currentLessonName",
            contentEdgeToEdge = true,
            trailingContent = {
                EvalCircleButton(onClick = { presetDialogShown = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_config),
                        contentDescription = "评教预设",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                body()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.backToList() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = 0.n1 withNight 100.n1
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentCourseName,
                        color = 0.n1 withNight 100.n1,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${currentTeacher?.teacherName.orEmpty()} · $currentLessonName",
                        color = 30.n1 withNight 90.n1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { presetDialogShown = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "评教预设",
                        tint = 0.n1 withNight 100.n1
                    )
                }
            }

            HorizontalDivider(color = 90.n1 withNight 30.n1, thickness = 0.5.dp)

            body()
        }
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = 100.n1 withNight 20.n1,
        titleContentColor = 0.n1 withNight 100.n1,
        textContentColor = 30.n1 withNight 90.n1,
        title = {
            Text("评教预设")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "匿名提交",
                        color = 0.n1 withNight 100.n1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = anonymous,
                        onCheckedChange = { anonymous = it }
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
                            CircularProgressIndicator()
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
                            modifier = Modifier.verticalScroll(rememberScrollState()),
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
        },
        confirmButton = {
            TextButton(
                enabled = presetQuestions.isNotEmpty(),
                onClick = {
                    viewModel.savePreset(
                        radioOptionIndexes = radioIndexes,
                        textAnswers = presetTextAnswers,
                        anonymous = anonymous
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = 40.a1 withNight 80.a1,
                    disabledContentColor = 60.n1 withNight 50.n1
                )
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = 40.a1 withNight 80.a1
                )
            ) {
                Text("取消")
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
                            .clickable { onOptionIndexChange(optionIndex) }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = 100.n1 withNight 20.n1
        ),
        shape = SmoothRoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    Text(text = "*", color = Color(0xFFE53935), fontSize = 14.sp)
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
                                .clickable { onSelect(optionId) }
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
