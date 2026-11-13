package com.ahu.ahutong.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 主页 AI 入口只由“开启 AI 服务”开关控制。
 * 是否配置模型不在这里判断：未配置时仍显示入口，点击后提示并跳转 AI 设置。
 */
object AssistantAccessState {

    private var initialized = false

    var enabled by mutableStateOf(true)
        private set

    fun ensureLoaded() {
        if (initialized) return
        initialized = true
        refresh()
    }

    fun refresh() {
        enabled = runCatching { AssistantPreferences.serviceEnabled() }.getOrDefault(true)
    }
}
