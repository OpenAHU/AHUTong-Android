package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilityKind
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.google.gson.JsonObject

/**
 * 动作类能力：只把用户带到既有页面，由原页面完成确认与执行。
 *
 * AI 不代扣款、不提交评教、不发邮件；删掉 AI 层后这些页面照旧可用。
 */
class AssistantNavigateCapability(
    override val spec: AssistantCapabilitySpec,
    private val detail: (JsonObject) -> String? = { null },
) : AssistantCapability {

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val extra = detail(arguments)?.takeIf { it.isNotBlank() }
        val summary = buildString {
            append("已为你打开「")
            append(spec.title)
            append("」")
            append("，请在原页面确认")
            if (extra != null) {
                append("：")
                append(extra)
            }
            append("。")
        }
        return AssistantOutcome.Success(AssistantAnswer(summary = summary, route = spec.openRoute))
    }
}

internal fun navigateCapabilities(): List<AssistantCapability> = listOf(
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "recharge_card",
            title = "校园卡充值",
            description = "打开校园卡充值页。AI 不代扣款，金额与支付都在原页面由用户确认。",
            parameters = schema(
                "amount" to stringProp("充值金额，单位元；只用于提示，不会自动填入"),
                "bank" to stringProp("扣款银行卡：agricultural=中国农业银行；cmb=招商银行"),
            ),
            sensitivity = AssistantSensitivity.FINANCIAL,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "一卡通系统",
            openRoute = "card_balance_deposit",
        ),
        detail = { args -> args.stringArg("amount")?.let { "充值金额 ¥$it" } },
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "recharge_electricity",
            title = "宿舍电费充值",
            description = "打开电费充值页。AI 不代扣款，房间、金额与支付密码都在原页面由用户确认。",
            parameters = schema("amount" to stringProp("充值金额，单位元；只用于提示，不会自动填入")),
            sensitivity = AssistantSensitivity.FINANCIAL,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "电费系统",
            openRoute = "electricity_pay",
        ),
        detail = { args -> args.stringArg("amount")?.let { "充值金额 ¥$it" } },
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "recharge_bathroom",
            title = "浴室缴费",
            description = "打开浴室缴费页。AI 不代扣款，浴室、手机号、金额与支付密码都在原页面确认。",
            parameters = schema(
                "bathroom" to stringProp("浴室：「竹园/龙河」或「桔园/蕙园」"),
                "phone" to stringProp("浴室账户绑定的 11 位手机号"),
                "amount" to stringProp("缴费金额，单位元"),
            ),
            sensitivity = AssistantSensitivity.FINANCIAL,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "浴室缴费系统",
            openRoute = "bathroom_deposit",
        ),
        detail = { args -> args.stringArg("amount")?.let { "缴费金额 ¥$it" } },
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "recharge_network",
            title = "网费充值",
            description = "打开网费充值页。AI 不代扣款，金额与支付密码都在原页面由用户确认。",
            parameters = schema("amount" to stringProp("充值金额，单位元；只用于提示，不会自动填入")),
            sensitivity = AssistantSensitivity.FINANCIAL,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "网费系统",
            openRoute = "network_recharge",
        ),
        detail = { args -> args.stringArg("amount")?.let { "充值金额 ¥$it" } },
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "submit_evaluation",
            title = "提交评教",
            description = "打开评教页。AI 不代填、不提交，具体问卷和评语由用户在原页面完成。",
            parameters = schema("semester" to stringProp("学期名称；只用于提示")),
            sensitivity = AssistantSensitivity.ACADEMIC,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "评教系统",
            openRoute = "evaluation",
        ),
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "send_mail",
            title = "发送学生邮箱邮件",
            description = "打开学生邮箱。AI 不代发邮件，收件人、正文与发送都在原页面由用户确认。",
            parameters = schema(
                "to" to stringProp("收件人邮箱"),
                "subject" to stringProp("主题"),
                "body" to stringProp("正文"),
            ),
            sensitivity = AssistantSensitivity.PUBLIC,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "学生邮箱",
            openRoute = "student_mail",
        ),
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "publish_lost_found",
            title = "发布失物招领",
            description = "打开失物招领页。AI 不代发布，分类、校区和联系方式由用户在原页面填写。",
            parameters = schema(
                "state" to intProp("1=失物招领，2=寻物启事", minimum = 1, maximum = 2),
                "title" to stringProp("标题"),
            ),
            sensitivity = AssistantSensitivity.PUBLIC,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "失物招领",
            openRoute = "lost_found",
        ),
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "delete_lost_found",
            title = "删除失物招领",
            description = "打开失物招领页。AI 不代删除，请在原页面选择自己的条目再删除。",
            parameters = schema("id" to stringProp("信息 ID")),
            sensitivity = AssistantSensitivity.PUBLIC,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "失物招领",
            openRoute = "lost_found",
        ),
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "switch_electricity_condition",
            title = "切换电控条件",
            description = "打开最近房间页。AI 不改本机电费房间，请在原页面选择要切换的电控条件。",
            parameters = schema(
                "index" to intProp("条件序号，从 0 开始", minimum = 0),
                "label" to stringProp("条件名称"),
                "room" to stringProp("房间名称"),
            ),
            sensitivity = AssistantSensitivity.FINANCIAL,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "电费系统",
            openRoute = "electricity_recent_rooms",
        ),
    ),
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "add_calendar_event",
            title = "校历",
            description = "打开校历页。安大通没有本地日历写入，日程以校历页面为准。",
            parameters = emptySchema(),
            sensitivity = AssistantSensitivity.PUBLIC,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "本地校历",
            openRoute = "school_calendar",
        ),
    ),
)
