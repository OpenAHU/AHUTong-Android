package com.ahu.ahutong.assistant.safety

import com.ahu.ahutong.assistant.AssistantCapabilityKind
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred

enum class AssistantPolicyDecision {
    /** 只读能力：直接执行。 */
    ALLOW,

    /** 写操作：必须在 UI 上得到用户确认后才执行。 */
    REQUIRE_APPROVAL,
}

/**
 * 工具策略：能力自己声明 kind/sensitivity，这里统一决定"要不要停下来问人"。
 * 当前没有任何写能力，但策略先立住，后续加充值/评教时不需要改运行时。
 */
object AssistantToolPolicy {

    fun decide(spec: AssistantCapabilitySpec): AssistantPolicyDecision = when {
        // 非写能力也可能要求确认（例如解题前的学术诚信确认）。
        spec.requiresConfirmation -> AssistantPolicyDecision.REQUIRE_APPROVAL

        spec.kind == AssistantCapabilityKind.READ || spec.kind == AssistantCapabilityKind.NAVIGATE ->
            AssistantPolicyDecision.ALLOW

        spec.sensitivity == AssistantSensitivity.FINANCIAL ->
            AssistantPolicyDecision.REQUIRE_APPROVAL

        else -> AssistantPolicyDecision.REQUIRE_APPROVAL
    }

    fun riskText(spec: AssistantCapabilitySpec): String = when {
        spec.confirmationNote != null -> spec.confirmationNote
        spec.sensitivity == AssistantSensitivity.FINANCIAL -> "涉及资金，需要本机确认"
        spec.kind == AssistantCapabilityKind.WRITE -> "会修改数据，需要确认"
        spec.requiresConfirmation -> "需要你确认后 AI 才会继续"
        else -> "只读操作"
    }
}

data class AssistantApprovalRequest(
    val id: String,
    val toolId: String,
    val title: String,
    val summary: String,
    val risk: String,
    val arguments: JsonObject = JsonObject(),
    val requiresPaymentPassword: Boolean = false,
    /** 本轮读过外部内容时，提示用户不要被网页/邮件/通知里的文字带着走。 */
    val untrustedWarning: String? = null,
    /** 确认按钮文案；解题确认用「继续解答」。 */
    val confirmationAction: String = "确认执行",
)

data class AssistantApprovalDecision(
    val approved: Boolean,
    /** 只在本机内存里传给写能力，不进模型上下文、不写历史。 */
    val paymentPassword: String? = null,
)

/**
 * 写操作的暂停点：运行时创建 deferred 并挂起，UI 通过 approve/reject 放行。
 * Android 侧在主线程调用，运行时在任意线程 await。
 */
class AssistantApprovalGate {

    private var pending: CompletableDeferred<AssistantApprovalDecision>? = null

    @Synchronized
    fun prepare(): CompletableDeferred<AssistantApprovalDecision> {
        pending?.cancel()
        return CompletableDeferred<AssistantApprovalDecision>().also { pending = it }
    }

    @Synchronized
    fun approve(paymentPassword: String? = null) {
        pending?.complete(AssistantApprovalDecision(approved = true, paymentPassword = paymentPassword))
    }

    @Synchronized
    fun reject() {
        pending?.complete(AssistantApprovalDecision(approved = false))
    }

    @Synchronized
    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
