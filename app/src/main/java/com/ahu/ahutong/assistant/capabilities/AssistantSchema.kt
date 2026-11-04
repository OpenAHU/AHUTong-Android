package com.ahu.ahutong.assistant.capabilities

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * 工具参数的 JSON Schema 小工具。能力只描述自己，不解析页面状态。
 */
internal fun emptySchema(): JsonObject =
    JsonParser.parseString("""{"type":"object","properties":{},"additionalProperties":false}""")
        .asJsonObject

internal fun schema(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
): JsonObject = JsonObject().apply {
    addProperty("type", "object")
    add(
        "properties",
        JsonObject().apply { properties.forEach { (name, value) -> add(name, value) } }
    )
    add("required", JsonArray().apply { required.forEach { add(JsonPrimitive(it)) } })
    addProperty("additionalProperties", false)
}

internal fun stringProp(description: String, enumValues: List<String>? = null): JsonObject =
    JsonObject().apply {
        addProperty("type", "string")
        addProperty("description", description)
        enumValues?.let { values ->
            add("enum", JsonArray().apply { values.forEach { add(JsonPrimitive(it)) } })
        }
    }

internal fun intProp(description: String, minimum: Int? = null, maximum: Int? = null): JsonObject =
    JsonObject().apply {
        addProperty("type", "integer")
        addProperty("description", description)
        minimum?.let { addProperty("minimum", it) }
        maximum?.let { addProperty("maximum", it) }
    }

internal fun numberProp(
    description: String,
    minimum: Double? = null,
    maximum: Double? = null,
): JsonObject = JsonObject().apply {
    addProperty("type", "number")
    addProperty("description", description)
    minimum?.let { addProperty("minimum", it) }
    maximum?.let { addProperty("maximum", it) }
}

internal fun boolProp(description: String): JsonObject = JsonObject().apply {
    addProperty("type", "boolean")
    addProperty("description", description)
}

internal fun stringArrayProp(description: String): JsonObject = JsonObject().apply {
    addProperty("type", "array")
    addProperty("description", description)
    add("items", JsonObject().apply { addProperty("type", "string") })
}

internal fun JsonObject.stringArg(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }
        ?.let { runCatching { it.asString }.getOrNull() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

internal fun JsonObject.intArg(key: String): Int? =
    get(key)?.takeIf { !it.isJsonNull }
        ?.let { runCatching { it.asInt }.getOrNull() }

internal fun JsonObject.boolArg(key: String, default: Boolean = false): Boolean =
    get(key)?.takeIf { !it.isJsonNull }
        ?.let { runCatching { it.asBoolean }.getOrNull() }
        ?: default

internal fun JsonObject.stringListArg(key: String): List<String> =
    get(key)?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { runCatching { it.asString }.getOrNull() }
        .orEmpty()

internal fun formatMoney(value: Double): String = String.format(java.util.Locale.CHINA, "%.2f", value)
