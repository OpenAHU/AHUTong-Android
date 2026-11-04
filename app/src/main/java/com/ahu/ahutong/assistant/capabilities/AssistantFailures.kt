package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.core.common.AhuError

/**
 * 把数据层错误原样翻译成能力层错误：不允许 AI 入口把网络异常说成登录失效。
 */
internal fun AhuError.asAssistantFailure(): AssistantOutcome.Failure = when (this) {
    AhuError.Network, AhuError.Timeout ->
        AssistantOutcome.Failure("network", "网络暂时不可用，稍后再试", retryable = true)
    is AhuError.Unauthorized ->
        AssistantOutcome.Failure("unauthorized", message)
    is AhuError.Server ->
        AssistantOutcome.Failure("server", message, retryable = true)
    is AhuError.ProtocolChanged ->
        AssistantOutcome.Failure("protocol", "上游返回格式变了，可能需要更新 App")
    is AhuError.Unknown ->
        AssistantOutcome.Failure("unknown", message)
}
