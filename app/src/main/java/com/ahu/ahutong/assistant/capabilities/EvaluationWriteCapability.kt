package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilityKind
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.EvalQuestionnaireForm
import com.ahu.ahutong.data.EvaluationRepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.EvalAnswerOption
import com.ahu.ahutong.data.model.EvalPreset
import com.ahu.ahutong.data.model.EvalQuestion
import com.ahu.ahutong.data.model.EvalQuestionAnswer
import com.ahu.ahutong.data.model.EvalQuestionnaireRes
import com.ahu.ahutong.data.model.EvalSubmitRequest
import com.ahu.ahutong.data.model.EvalTask
import com.ahu.ahutong.data.model.EvalTaskItem
import com.ahu.ahutong.data.model.EvalTeacher
import com.ahu.ahutong.data.session.SessionStore
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException

/**
 * 按本机评教预设提交评教。真实提交，必须经确认卡；确认卡只展示范围和教师，不展示问卷答案。
 */
class SubmitEvaluationCapability(
    private val semesters: suspend () -> AhuResult<List<com.ahu.ahutong.data.model.EvalSemester>> =
        { EvaluationRepository.getSemesters() },
    private val tasks: suspend (String) -> AhuResult<List<EvalTaskItem>> =
        { semesterId -> EvaluationRepository.getEvaluationList(semesterId, evaluated = false) },
    private val questions: suspend (String) -> AhuResult<EvalQuestionnaireForm> =
        { questionnaireId -> EvaluationRepository.getQuestions(questionnaireId) },
    private val preset: () -> EvalPreset = { AHUCache.getEvalPreset() },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "submit_evaluation",
        title = "按预设提交评教",
        description = "按每个问卷的第一项和本机评语预设代填评教，提交后不可撤回。只有用户明确要求提交评教时才能调用。" +
            "teacher 指定教师姓名；all=true 表示提交该学期全部待评教师。",
        parameters = schema(
            "semester" to stringProp("学期名称或 ID；不传用最近学期"),
            "teacher" to stringProp("教师姓名；与 all 二选一"),
            "all" to boolProp("是否提交该学期全部待评教师，默认 false"),
        ),
        sensitivity = AssistantSensitivity.ACADEMIC,
        kind = AssistantCapabilityKind.WRITE,
        source = "评教系统",
        openRoute = "evaluation",
        requiresConfirmation = true,
    )

    override suspend fun describe(arguments: JsonObject): String? {
        if (arguments.boolArg("all", false)) return "范围：该学期全部待评教师"
        return arguments.stringArg("teacher")?.let { "教师：$it" }
    }

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("confirmation_required", "需要本机确认后才能提交评教")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val semesterList = when (val result = semesters()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val keyword = arguments.stringArg("semester")
        val selected = semesterList.firstOrNull { it.id == keyword }
            ?: semesterList.firstOrNull {
                keyword != null && (it.nameZh.contains(keyword) || it.schoolYear.contains(keyword))
            }
            ?: semesterList.firstOrNull()
            ?: return AssistantOutcome.Failure("evaluation_empty", "没有可评教学期")
        val items = when (val result = tasks(selected.id)) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val teacherName = arguments.stringArg("teacher")
        val all = arguments.boolArg("all", false)
        if (!all && teacherName == null) {
            return AssistantOutcome.Failure("invalid_arguments", "请指定 teacher，或把 all 设为 true 提交全部")
        }
        val targets = items.flatMap { item ->
            item.taskList.flatMap { task ->
                task.teachers.filter { teacher ->
                    (teacherName == null || teacher.teacherName.contains(teacherName)) &&
                        teacher.status == "TO_REVIEW" &&
                        task.timeStatus
                }.map { teacher -> task to teacher }
            }
        }
        if (targets.isEmpty()) {
            return AssistantOutcome.Failure("evaluation_empty", "没有匹配的待评教师")
        }
        val failures = mutableListOf<String>()
        var uncertain = false
        var submitted = 0
        val presetValue = preset()
        targets.forEach { (task, teacher) ->
            when (val result = submitOne(task, teacher, presetValue)) {
                is AhuResult.Success -> submitted++
                is AhuResult.Failure -> {
                    failures += "${teacher.teacherName}: ${result.error.toUserMessage()}"
                    if (result.error.uncertain()) uncertain = true
                }
            }
        }
        val semesterLabel = selected.nameZh.ifBlank { selected.schoolYear }
        return if (submitted == 0 && !uncertain) {
            AssistantOutcome.Failure(
                "evaluation_failed",
                "评教提交失败：" + failures.take(2).joinToString("；"),
            )
        } else if (uncertain || submitted > 0 && failures.isNotEmpty()) {
            AssistantOutcome.Failure(
                "uncertain",
                "评教提交结果未知：已成功 $submitted 项，${failures.size} 项需要核对；请先到评教页确认，避免重复提交",
                retryable = false,
            )
        } else {
            AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "已提交 $submitted 项评教",
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "evaluation_submitted",
                        headline = submitted.toString(),
                        headlineSuffix = "项",
                        subtitle = semesterLabel,
                    ),
                )
            )
        }
    }

    private suspend fun submitOne(
        task: EvalTask,
        teacher: EvalTeacher,
        presetValue: EvalPreset,
    ): AhuResult<Unit> {
        return try {
            val form = when (val result = questions(task.evaluationQuestionnaireId)) {
                is AhuResult.Failure -> return result
                is AhuResult.Success -> result.value
            }
            val request = buildRequest(task, teacher, form.questionnaire, form.questions, presetValue)
            val checkMessage = when (val result = EvaluationRepository.checkSubmit(request)) {
                is AhuResult.Failure -> return result
                is AhuResult.Success -> result.value
            }
            if (checkMessage.isNotBlank()) {
                return AhuResult.Failure(AhuError.ProtocolChanged(checkMessage))
            }
            EvaluationRepository.submit(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AhuResult.Failure(AhuError.Unknown(error.message ?: "评教提交失败"))
        }
    }

    private fun buildRequest(
        task: EvalTask,
        teacher: EvalTeacher,
        questionnaire: com.ahu.ahutong.data.model.EvalQuestionnaire,
        questions: List<EvalQuestion>,
        preset: EvalPreset,
    ): EvalSubmitRequest {
        val radioQuestions = questions.filter { it.attribute.typeId == 1 && it.options.isNotEmpty() }
        val radioIndexes = radioQuestions.associate { question ->
            val id = question.attribute.id.toString()
            val index = (preset.radioOptionIndexes[id] ?: preset.radioOptionIndex)
                .coerceIn(0, (question.options.size - 1).coerceAtLeast(0))
            id to index
        }.toMutableMap()
        if (radioIndexes.size >= 2 && radioIndexes.values.toSet().size == 1) {
            val target = radioQuestions.getOrNull(1)
            if (target != null && target.options.size >= 2) {
                val id = target.attribute.id.toString()
                val current = radioIndexes[id] ?: 0
                radioIndexes[id] = (current + 1).coerceAtMost(target.options.size - 1)
            }
        }
        val answers = questions.map { question ->
            val attr = question.attribute
            val questionId = attr.id.toString()
            when (attr.typeId) {
                1 -> {
                    val option = question.options.getOrNull(radioIndexes[questionId] ?: 0)
                        ?: question.options.firstOrNull()
                    EvalQuestionAnswer(
                        questionId = questionId,
                        questionnaireId = questionnaire.id,
                        type = "1",
                        score = if (attr.enableScore) option?.optionScore ?: 0.0 else 0.0,
                        answer = null,
                        questionAnsExpSaveList = listOf(
                            EvalAnswerOption(
                                optionId = option?.optionId?.toString().orEmpty(),
                                questionnaireId = questionnaire.id,
                            )
                        ),
                    )
                }
                4 -> EvalQuestionAnswer(
                    questionId = questionId,
                    questionnaireId = questionnaire.id,
                    type = "4",
                    score = 0.0,
                    answer = preset.textAnswers[questionId]
                        ?: preset.textAnswer.ifBlank { EvalPreset().textAnswer },
                    questionAnsExpSaveList = emptyList(),
                )
                else -> EvalQuestionAnswer(
                    questionId = questionId,
                    questionnaireId = questionnaire.id,
                    type = attr.typeId.toString(),
                    score = 0.0,
                    answer = null,
                    questionAnsExpSaveList = emptyList(),
                )
            }
        }
        return EvalSubmitRequest(
            id = null,
            stdSumTaskId = teacher.stdSumTaskId.ifBlank { task.stdSumTaskId },
            anonymous = preset.anonymous,
            evaluationQuestionnaireRes = EvalQuestionnaireRes(
                id = null,
                questionnaireId = questionnaire.id,
                questionnaireName = questionnaire.nameZh.ifBlank { task.evaluationQuestionnaireName },
                enable = questionnaire.enable,
                answer = "[]",
                score = 0.0,
                questionAnsSaveList = answers,
            ),
        )
    }

    private fun AhuError.uncertain(): Boolean = when (this) {
        AhuError.Network, AhuError.Timeout, is AhuError.ProtocolChanged, is AhuError.Unknown -> true
        else -> false
    }

    private fun AhuError.toUserMessage(): String = when (this) {
        AhuError.Network, AhuError.Timeout -> "网络异常"
        is AhuError.Unauthorized -> message
        is AhuError.Server -> message
        is AhuError.ProtocolChanged -> detail
        is AhuError.Unknown -> message
    }
}
