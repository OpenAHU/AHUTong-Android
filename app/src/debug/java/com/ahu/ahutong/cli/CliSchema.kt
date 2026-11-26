package com.ahu.ahutong.cli

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object CliSchema {
    fun of(fields: String): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        addProperty("additionalProperties", false)
        val required = JsonArray()
        add("properties", JsonObject().apply {
            fields.split(' ').filter(String::isNotBlank).forEach { field ->
                val (key, type) = field.split(':', limit = 2)
                val name = key.removeSuffix("?")
                if (!key.endsWith('?')) required.add(name)
                add(name, JsonObject().apply { addProperty("type", type) })
            }
        })
        add("required", required)
    }

    fun validate(schema: JsonObject, value: JsonElement, path: String = "arguments") {
        if (schema.has("enum")) require(schema.getAsJsonArray("enum").any { it == value }) { path }
        val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        when (schema.get("type")?.asString) {
            "object" -> {
                require(value.isJsonObject) { path }
                val obj = value.asJsonObject
                val properties = schema.getAsJsonObject("properties") ?: JsonObject()
                schema.getAsJsonArray("required")?.forEach { require(obj.has(it.asString)) { path } }
                if (schema.get("additionalProperties")?.isJsonPrimitive == true &&
                    !schema.get("additionalProperties").asBoolean) {
                    require(obj.keySet().all(properties::has)) { path }
                }
                obj.entrySet().forEach { (key, entry) ->
                    properties.getAsJsonObject(key)?.let { validate(it, entry, "$path.$key") }
                }
            }
            "array" -> {
                require(value.isJsonArray) { path }
                require(value.asJsonArray.size() <= 500) { path }
                schema.getAsJsonObject("items")?.let { item ->
                    value.asJsonArray.forEach { validate(item, it, path) }
                }
            }
            "string" -> require(primitive?.isString == true && primitive.asString.length <= 32_000) { path }
            "boolean" -> require(primitive?.isBoolean == true) { path }
            "integer", "number" -> {
                require(primitive?.isNumber == true) { path }
                val number = primitive.asBigDecimal
                if (schema.get("type").asString == "integer") require(number.stripTrailingZeros().scale() <= 0) { path }
                schema.get("minimum")?.let { require(number >= it.asBigDecimal) { path } }
                schema.get("maximum")?.let { require(number <= it.asBigDecimal) { path } }
            }
        }
    }
}
