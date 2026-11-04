package com.ahu.ahutong.assistant

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 能力注册表：AI 入口只认识这张表，不认识任何具体页面或 ViewModel。
 *
 * [toolSchemas] 已按 OpenAI tools 的形状输出，后续接模型网关时不需要改能力实现。
 */
class AssistantCapabilityRegistry(capabilities: List<AssistantCapability>) {

    private val ordered = capabilities.toList()
    private val byId = ordered.associateBy { it.spec.id }

    val all: List<AssistantCapability> get() = ordered

    fun find(id: String): AssistantCapability? = byId[id]

    fun toolSchemas(): JsonArray = JsonArray().apply {
        ordered.forEach { capability ->
            add(
                JsonObject().apply {
                    addProperty("type", "function")
                    add(
                        "function",
                        JsonObject().apply {
                            addProperty("name", capability.spec.id)
                            addProperty("description", capability.spec.description)
                            add("parameters", capability.spec.parameters)
                        }
                    )
                }
            )
        }
    }
}
