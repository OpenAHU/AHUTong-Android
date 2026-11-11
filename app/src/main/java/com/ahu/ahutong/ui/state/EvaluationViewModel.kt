package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.onFailure
import com.ahu.ahutong.core.common.onSuccess
import com.ahu.ahutong.data.EvaluationRepository
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.EvalAnswerOption
import com.ahu.ahutong.data.model.EvalPreset
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalQuestionAnswer
import com.ahu.ahutong.data.model.EvalQuestionnaire
import com.ahu.ahutong.data.model.EvalQuestionnaireRes
import com.ahu.ahutong.data.model.EvalSemester
import com.ahu.ahutong.data.model.EvalSubmitRequest
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.EvalTaskItem
import com.ahu.ahutong.data.model.EvalTeacher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EvaluationViewModel : ViewModel() {

    val semesters = MutableStateFlow<List<EvalSemester>>(emptyList())
    val selectedSemesterId = MutableStateFlow("")
    val taskItems = MutableStateFlow<List<EvalTaskItem>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    val questions = MutableStateFlow<List<EvalQuestion>>(emptyList())
    val currentQuestionnaire = MutableStateFlow<EvalQuestionnaire?>(null)
    val currentTask = MutableStateFlow<EvalTask?>(null)
    val currentTeacher = MutableStateFlow<EvalTeacher?>(null)
    val currentCourseName = MutableStateFlow("")
    val currentLessonName = MutableStateFlow("")
    val answers = MutableStateFlow<Map<String, String>>(emptyMap())
    val textAnswers = MutableStateFlow<Map<String, String>>(emptyMap())
    val isSubmitting = MutableStateFlow(false)
    val isBulkSubmitting = MutableStateFlow(false)
    val submitSuccess = MutableStateFlow(false)
    val submitMessage = MutableStateFlow<String?>(null)
    val preset = MutableStateFlow(AHUCache.getEvalPreset())
    val presetQuestions = MutableStateFlow<List<EvalQuestion>>(emptyList())
    val isPresetLoading = MutableStateFlow(false)
    val presetActionMessage = MutableStateFlow<String?>(null)
    private var listLoadJob: Job? = null

    fun loadSemesters() {
        listLoadJob?.cancel()
        listLoadJob = viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val semestersResult = EvaluationRepository.getSemesters()
                val items = semestersResult.valueOrNull()
                if (items == null) {
                    errorMessage.value = semestersResult.errorOrNull()?.toUserMessage() ?: "加载学期失败"
                    return@launch
                }
                semesters.value = items
                if (selectedSemesterId.value.isEmpty() && items.isNotEmpty()) {
                    val currentSemesterId = EvaluationRepository.getCurrentSemesterId()
                    selectedSemesterId.value = items.firstOrNull {
                        it.id == currentSemesterId
                    }?.id ?: items.first().id
                }
                val semesterId = selectedSemesterId.value
                if (semesterId.isNotEmpty()) {
                    EvaluationRepository.getEvaluationList(semesterId)
                        .onSuccess { taskItems.value = it }
                        .onFailure { errorMessage.value = it.toUserMessage() }
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadEvaluationList() {
        val semesterId = selectedSemesterId.value
        if (semesterId.isEmpty()) return

        listLoadJob?.cancel()
        listLoadJob = viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                EvaluationRepository.getEvaluationList(semesterId)
                    .onSuccess { taskItems.value = it }
                    .onFailure { errorMessage.value = it.toUserMessage() }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun enterEvaluation(
        task: EvalTask,
        teacher: EvalTeacher,
        courseName: String,
        lessonName: String
    ) {
        currentTask.value = task
        currentTeacher.value = teacher
        currentCourseName.value = courseName
        currentLessonName.value = lessonName

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            answers.value = emptyMap()
            textAnswers.value = emptyMap()
            submitSuccess.value = false
            submitMessage.value = null
            currentQuestionnaire.value = null
            questions.value = emptyList()
            EvaluationRepository.getQuestions(task.evaluationQuestionnaireId)
                .onSuccess { form ->
                    currentQuestionnaire.value = form.questionnaire
                    questions.value = form.questions
                    if (presetQuestions.value.isEmpty()) {
                        presetQuestions.value = form.questions
                    }
                }
                .onFailure { errorMessage.value = it.toUserMessage() }
            isLoading.value = false
        }
    }

    fun backToList() {
        currentTask.value = null
        currentTeacher.value = null
        currentCourseName.value = ""
        currentLessonName.value = ""
        currentQuestionnaire.value = null
        questions.value = emptyList()
        answers.value = emptyMap()
        textAnswers.value = emptyMap()
        submitSuccess.value = false
        submitMessage.value = null
    }

    fun setAnswer(questionId: String, optionId: String) {
        answers.value = answers.value + (questionId to optionId)
    }

    fun setTextAnswer(questionId: String, text: String) {
        textAnswers.value = textAnswers.value + (questionId to text)
    }

    fun savePreset(radioOptionIndex: Int, textAnswer: String, anonymous: Boolean) {
        val normalized = EvalPreset(
            radioOptionIndex = radioOptionIndex.coerceAtLeast(0),
            textAnswer = textAnswer.ifBlank { EvalPreset().textAnswer },
            anonymous = anonymous
        )
        preset.value = normalized
        AHUCache.saveEvalPreset(normalized)
        presetActionMessage.value = "预设已保存"
    }

    fun savePreset(
        radioOptionIndexes: Map<String, Int>,
        textAnswers: Map<String, String>,
        anonymous: Boolean
    ) {
        val templateQuestions = presetQuestions.value.ifEmpty { questions.value }
        val normalizedRadioIndexes = normalizeRadioOptionIndexes(templateQuestions, radioOptionIndexes)
        val normalizedTextAnswers = textAnswers
            .mapValues { it.value.ifBlank { EvalPreset().textAnswer } }
            .filterKeys { questionId ->
                templateQuestions.any { it.attribute.id.toString() == questionId }
            }
        val defaultTextAnswer = normalizedTextAnswers.values.firstOrNull()
            ?: EvalPreset().textAnswer
        val normalized = EvalPreset(
            radioOptionIndex = normalizedRadioIndexes.values.firstOrNull() ?: 0,
            radioOptionIndexes = normalizedRadioIndexes,
            textAnswer = defaultTextAnswer,
            textAnswers = normalizedTextAnswers,
            anonymous = anonymous
        )
        preset.value = normalized
        AHUCache.saveEvalPreset(normalized)
        presetActionMessage.value = "预设已保存"
    }

    fun loadPresetQuestions() {
        if (presetQuestions.value.isNotEmpty()) return
        if (isPresetLoading.value) return
        questions.value.takeIf { it.isNotEmpty() }?.let {
            presetQuestions.value = it
            return
        }

        val questionnaireId = taskItems.value
            .asSequence()
            .flatMap { it.taskList.asSequence() }
            .firstOrNull { it.evaluationQuestionnaireId.isNotBlank() }
            ?.evaluationQuestionnaireId
            ?: return

        viewModelScope.launch {
            isPresetLoading.value = true
            EvaluationRepository.getQuestions(questionnaireId)
                .onSuccess { presetQuestions.value = it.questions }
                .onFailure { presetActionMessage.value = it.toUserMessage() }
            isPresetLoading.value = false
        }
    }

    fun presetRadioOptionIndexesFor(
        questions: List<EvalQuestion>,
        presetValue: EvalPreset = preset.value
    ): Map<String, Int> {
        val radioQuestions = questions.filter { it.attribute.typeId == 1 && it.options.isNotEmpty() }
        val radioQuestionIds = radioQuestions.map { it.attribute.id.toString() }
        val indexes = radioQuestions.associate { question ->
            val questionId = question.attribute.id.toString()
            val optionCount = question.options.size
            val index = presetValue.radioOptionIndexes[questionId]
                ?: fallbackRadioOptionIndex(
                    questionId = questionId,
                    radioQuestionIds = radioQuestionIds,
                    optionCount = optionCount,
                    presetValue = presetValue
                )
            questionId to index.coerceIn(0, optionCount - 1)
        }
        return ensureOneDifferentRadioIndex(indexes, radioQuestions)
    }

    fun presetTextAnswersFor(
        questions: List<EvalQuestion>,
        presetValue: EvalPreset = preset.value
    ): Map<String, String> {
        return questions
            .filter { it.attribute.typeId == 4 }
            .associate { question ->
                val questionId = question.attribute.id.toString()
                questionId to (
                    presetValue.textAnswers[questionId]
                        ?: presetValue.textAnswer.ifBlank { EvalPreset().textAnswer }
                    )
            }
    }

    fun applyPresetToCurrent() {
        val presetValue = preset.value
        val presetAnswers = buildPresetAnswers(questions.value, presetValue)
        answers.value = presetAnswers.radioAnswers
        textAnswers.value = presetAnswers.textAnswers
        presetActionMessage.value = "已套用预设"
    }

    fun submitCurrentWithPreset() {
        applyPresetToCurrent()
        submit(preset.value.anonymous)
    }

    fun quickSubmitWithPreset(
        task: EvalTask,
        teacher: EvalTeacher,
        courseName: String,
        lessonName: String
    ) {
        viewModelScope.launch {
            isSubmitting.value = true
            presetActionMessage.value = null
            submitEvaluationWithPreset(task, teacher)
                .onSuccess {
                    presetActionMessage.value = "已按预设完成：${teacher.teacherName} · $courseName"
                    loadEvaluationList()
                }
                .onFailure {
                    presetActionMessage.value = it.toUserMessage()
                }
            isSubmitting.value = false
        }
    }

    fun submitAllWithPreset() {
        val targets = taskItems.value.flatMap { item ->
            item.taskList.flatMap { task ->
                task.teachers
                    .filter { teacher -> teacher.status == "TO_REVIEW" && task.timeStatus }
                    .map { teacher -> task to teacher }
            }
        }
        if (targets.isEmpty()) {
            presetActionMessage.value = "暂无可按预设完成的评教"
            return
        }

        viewModelScope.launch {
            isBulkSubmitting.value = true
            presetActionMessage.value = null
            var successCount = 0
            var firstError: String? = null

            targets.forEach { (task, teacher) ->
                val result = submitEvaluationWithPreset(task, teacher)
                if (result.isSuccess) {
                    successCount++
                } else if (firstError == null) {
                    firstError = result.errorOrNull()?.toUserMessage() ?: "预设提交失败"
                }
            }

            presetActionMessage.value = if (firstError == null) {
                "已按预设完成 $successCount 项评教"
            } else {
                "已完成 $successCount/${targets.size} 项，失败：$firstError"
            }
            loadEvaluationList()
            isBulkSubmitting.value = false
        }
    }

    fun submit(anonymous: Boolean = false) {
        val task = currentTask.value ?: return
        val teacher = currentTeacher.value ?: return
        val questionnaire = currentQuestionnaire.value ?: return
        val validationError = validateRequiredAnswers()
        if (validationError != null) {
            submitMessage.value = validationError
            return
        }

        viewModelScope.launch {
            isSubmitting.value = true
            submitMessage.value = null

            val request = buildSubmitRequest(
                task = task,
                teacher = teacher,
                questionnaire = questionnaire,
                anonymous = anonymous,
                questions = questions.value,
                answers = answers.value,
                textAnswers = textAnswers.value
            )

            val checkResult = EvaluationRepository.checkSubmit(request)
            if (checkResult.isFailure) {
                submitMessage.value = checkResult.errorOrNull()?.toUserMessage() ?: "提交检查失败"
                isSubmitting.value = false
                return@launch
            }
            val checkMessage = checkResult.valueOrNull().orEmpty()
            if (checkMessage.isNotBlank()) {
                submitMessage.value = checkMessage
                isSubmitting.value = false
                return@launch
            }

            EvaluationRepository.submit(request)
                .onSuccess {
                    submitSuccess.value = true
                    submitMessage.value = "提交成功"
                    loadEvaluationList()
                }
                .onFailure {
                    submitMessage.value = it.toUserMessage()
                }

            isSubmitting.value = false
        }
    }

    private fun validateRequiredAnswers(): String? {
        val firstMissing = questions.value.firstOrNull { question ->
            val questionId = question.attribute.id.toString()
            when (question.attribute.typeId) {
                1 -> question.attribute.required && answers.value[questionId].isNullOrBlank()
                4 -> question.attribute.required && textAnswers.value[questionId].isNullOrBlank()
                else -> question.attribute.required
            }
        } ?: return null

        return "请完成第 ${firstMissing.index} 题"
    }

    private fun buildSubmitRequest(
        task: EvalTask,
        teacher: EvalTeacher,
        questionnaire: EvalQuestionnaire,
        anonymous: Boolean,
        questions: List<EvalQuestion>,
        answers: Map<String, String>,
        textAnswers: Map<String, String>
    ): EvalSubmitRequest {
        val answerList = questions.map { question ->
            val attr = question.attribute
            val questionId = attr.id.toString()
            when (attr.typeId) {
                1 -> {
                    val selectedOptionId = answers[questionId].orEmpty()
                    val option = question.options.firstOrNull {
                        it.optionId.toString() == selectedOptionId
                    }
                    EvalQuestionAnswer(
                        questionId = questionId,
                        questionnaireId = questionnaire.id,
                        type = "1",
                        score = if (attr.enableScore) option?.optionScore ?: 0.0 else 0.0,
                        answer = null,
                        questionAnsExpSaveList = listOf(
                            EvalAnswerOption(
                                optionId = selectedOptionId,
                                questionnaireId = questionnaire.id
                            )
                        )
                    )
                }
                4 -> EvalQuestionAnswer(
                    questionId = questionId,
                    questionnaireId = questionnaire.id,
                    type = "4",
                    score = 0.0,
                    answer = textAnswers[questionId].orEmpty(),
                    questionAnsExpSaveList = emptyList()
                )
                else -> EvalQuestionAnswer(
                    questionId = questionId,
                    questionnaireId = questionnaire.id,
                    type = attr.typeId.toString(),
                    score = 0.0,
                    answer = null,
                    questionAnsExpSaveList = emptyList()
                )
            }
        }

        return EvalSubmitRequest(
            id = null,
            stdSumTaskId = teacher.stdSumTaskId.ifBlank { task.stdSumTaskId },
            anonymous = anonymous,
            evaluationQuestionnaireRes = EvalQuestionnaireRes(
                id = null,
                questionnaireId = questionnaire.id,
                questionnaireName = questionnaire.nameZh.ifBlank { task.evaluationQuestionnaireName },
                enable = questionnaire.enable,
                answer = "[]",
                score = 0.0,
                questionAnsSaveList = answerList
            )
        )
    }

    private suspend fun submitEvaluationWithPreset(
        task: EvalTask,
        teacher: EvalTeacher
    ): AhuResult<Unit> {
        val form = when (val result = EvaluationRepository.getQuestions(task.evaluationQuestionnaireId)) {
            is AhuResult.Success -> result.value
            is AhuResult.Failure -> return result
        }
        val presetAnswers = buildPresetAnswers(form.questions, preset.value)
        val request = buildSubmitRequest(
            task = task,
            teacher = teacher,
            questionnaire = form.questionnaire,
            anonymous = preset.value.anonymous,
            questions = form.questions,
            answers = presetAnswers.radioAnswers,
            textAnswers = presetAnswers.textAnswers
        )

        val checkMessage = when (val result = EvaluationRepository.checkSubmit(request)) {
            is AhuResult.Success -> result.value
            is AhuResult.Failure -> return result
        }
        if (checkMessage.isNotBlank()) {
            // 与仓储里 check(code == 0) 的语义一致：上游给了拒绝原因，用户文案就是它本身。
            return AhuResult.Failure(AhuError.ProtocolChanged(checkMessage))
        }

        return when (val result = EvaluationRepository.submit(request)) {
            is AhuResult.Success -> AhuResult.Success(Unit)
            is AhuResult.Failure -> result
        }
    }

    private fun buildPresetAnswers(
        questions: List<EvalQuestion>,
        preset: EvalPreset
    ): PresetAnswers {
        val radioOptionIndexes = presetRadioOptionIndexesFor(questions, preset)
        // 仅单选题（typeId == 1）需要预选；索引越界时夹到合法范围，无选项的题目直接跳过。
        val radioAnswers = questions
            .filter { it.attribute.typeId == 1 }
            .mapNotNull { question ->
                val questionId = question.attribute.id.toString()
                val optionIndex = radioOptionIndexes[questionId]
                    ?.coerceIn(0, question.options.lastIndex)
                    ?: 0
                question.options.getOrNull(optionIndex)
                    ?.let { questionId to it.optionId.toString() }
            }
            .toMap()

        return PresetAnswers(radioAnswers, presetTextAnswersFor(questions, preset))
    }

    private fun normalizeRadioOptionIndexes(
        questions: List<EvalQuestion>,
        indexes: Map<String, Int>
    ): Map<String, Int> {
        if (questions.isEmpty()) return indexes.mapValues { it.value.coerceAtLeast(0) }
        val radioQuestions = questions.filter { it.attribute.typeId == 1 && it.options.isNotEmpty() }
        val normalized = radioQuestions.associate { question ->
            val questionId = question.attribute.id.toString()
            val optionCount = question.options.size
            questionId to (indexes[questionId] ?: 0).coerceIn(0, optionCount - 1)
        }
        return ensureOneDifferentRadioIndex(normalized, radioQuestions)
    }

    private fun fallbackRadioOptionIndex(
        questionId: String,
        radioQuestionIds: List<String>,
        optionCount: Int,
        presetValue: EvalPreset
    ): Int {
        val baseIndex = presetValue.radioOptionIndex.coerceIn(0, (optionCount - 1).coerceAtLeast(0))
        if (presetValue.radioOptionIndexes.isNotEmpty()) return baseIndex

        val differentQuestionId = radioQuestionIds.getOrNull(1)
        return if (questionId == differentQuestionId && optionCount > 1) {
            if (baseIndex == 0) 1 else 0
        } else {
            baseIndex
        }
    }

    private fun ensureOneDifferentRadioIndex(
        indexes: Map<String, Int>,
        radioQuestions: List<EvalQuestion>
    ): Map<String, Int> {
        if (indexes.size < 2 || indexes.values.toSet().size > 1) return indexes
        val target = radioQuestions.getOrNull(1) ?: return indexes
        if (target.options.size < 2) return indexes

        val questionId = target.attribute.id.toString()
        val current = indexes[questionId] ?: return indexes
        val replacement = if (current == 0) 1 else 0
        return indexes + (questionId to replacement.coerceIn(0, target.options.size - 1))
    }

    private data class PresetAnswers(
        val radioAnswers: Map<String, String>,
        val textAnswers: Map<String, String>
    )
}
