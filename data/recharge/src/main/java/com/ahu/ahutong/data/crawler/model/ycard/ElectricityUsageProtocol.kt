package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.recharge.ElectricityDailyUsage
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate

data class ElectricityUsageHistoryPage(
    val records: List<ElectricityDailyUsage>,
    val total: Int?
)

/** Strict parsing of the A/B/C electricity wire format; missing values never mean zero. */
object ElectricityUsageProtocol {
    fun balance(body: String): AhuResult<Double> = parse(body) { response ->
        val map = response.getAsJsonObject("map") ?: error("缺少房间余额")
        val info = map.getAsJsonObject("showData")?.get("信息")?.asString
            ?: error("缺少剩余电量")
        val raw = BALANCE.find(info)?.groupValues?.get(1) ?: error("剩余电量格式已变化")
        raw.toDouble().also { require(it.isFinite()) }
    }

    fun history(body: String): AhuResult<ElectricityUsageHistoryPage> = parse(body) { response ->
        // A recordType-only response is the metadata request, not an empty usage history.
        val data = response.getAsJsonArray("data") ?: error("缺少用电记录")
        val records = data.map { element ->
            val item = element.asJsonObject
            val date = LocalDate.parse(item.get("日期")?.asString ?: error("缺少用电日期"))
            val raw = item.get("度数")?.asString ?: error("缺少用电量")
            val amount = ENERGY.matchEntire(raw.trim())?.groupValues?.get(1)
                ?: error("用电量单位或格式已变化")
            val kwh = amount.toDouble()
            require(kwh.isFinite() && kwh >= 0.0)
            ElectricityDailyUsage(date, kwh)
        }
        val total = response.get("total")?.let { value ->
            require(!value.isJsonNull)
            value.asString.toInt().also { require(it >= records.size) }
        }
        ElectricityUsageHistoryPage(records, total)
    }

    private fun <T> parse(body: String, value: (JsonObject) -> T): AhuResult<T> = try {
        val response = JsonParser.parseString(body).asJsonObject
        val code = response.get("code")?.asString?.toInt() ?: error("缺少响应状态")
        if (code != 200) {
            AhuResult.Failure(AhuError.Server(code, "电费查询未成功"))
        } else {
            AhuResult.Success(value(response))
        }
    } catch (_: Exception) {
        AhuResult.Failure(AhuError.ProtocolChanged("电费余额或用电记录格式已变化"))
    }

    private val BALANCE = Regex("房间当前剩余电量\\s*[:：]?\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(?:度|[，,]|$)")
    private val ENERGY = Regex("(\\d+(?:\\.\\d+)?)\\s*度")
}
