package com.ahu.ahutong.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore

/**
 * 主页 AI 入口的可用性：必须已保存模型配置，且至少成功测试过一次连通性。
 * 关键词路由已停用，所以未验证的配置不提供入口。
 */
object AssistantAccessState {

    private var initialized = false

    var ready by mutableStateOf(false)
        private set

    fun ensureLoaded() {
        if (initialized) return
        initialized = true
        refresh()
    }

    fun refresh() {
        ready = runCatching {
            AssistantModelConfigStore.load() != null && AssistantPreferences.connectionVerified()
        }.getOrDefault(false)
    }
}
