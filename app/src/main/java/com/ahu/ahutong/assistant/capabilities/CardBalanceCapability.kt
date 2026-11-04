package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.model.Card
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * 校园卡余额：只读 [AHURepository.getCardMoney]，不触发充值、不碰支付密码。
 * 余额属于金融类敏感数据，默认不交给第三方模型（见 [AssistantSensitivity.FINANCIAL]）。
 */
class CardBalanceCapability(
    private val load: suspend () -> AhuResult<Card> = { AHURepository.getCardMoney() },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "card_balance",
        title = "校园卡余额",
        description = "查询校园卡当前余额（只读，不涉及充值）。",
        parameters = JsonParser.parseString("""{"type":"object","properties":{},"additionalProperties":false}""")
            .asJsonObject,
        sensitivity = AssistantSensitivity.FINANCIAL,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        when (val result = load()) {
            is AhuResult.Success -> {
                val card = result.value
                val balance = card.balance
                val transition = card.transitionBalance
                val lines = buildList {
                    if (balance == null || !balance.isFinite()) return@buildList
                    if (transition != null && transition.isFinite() && transition > 0.0) {
                        add("过渡余额 ¥${format(transition)}（刷卡后转入）")
                    }
                }
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = if (balance == null || !balance.isFinite()) {
                            "余额暂时读不到，请稍后再试"
                        } else {
                            "校园卡余额 ¥${format(balance)}"
                        },
                        lines = lines,
                    )
                )
            }
            is AhuResult.Failure -> result.error.asAssistantFailure()
        }

    private fun format(value: Double): String = String.format(Locale.CHINA, "%.2f", value)
}
