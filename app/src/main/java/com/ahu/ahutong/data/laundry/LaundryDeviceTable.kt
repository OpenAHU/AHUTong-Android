package com.ahu.ahutong.data.laundry

import android.content.Context
import android.util.Log
import com.ahu.ahutong.BuildConfig
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 洗衣房设备台账：assets 基线 + 线上增量。
 *
 * 三层数据源（load() 的返回顺序）：
 * 1. 本地缓存（上次在线拉到的 table.json 快照，启动即生效）
 * 2. assets 基线（离线兜底，只在缓存比基线旧时才启用）
 * 3. 线上（openahu.org，If-None-Match → 200 落缓存 / 304 跳过）
 *
 * version 只升不降：缓存与 assets 同版不覆盖，线上回退旧版视为坏数据丢弃。
 */
@Singleton
class LaundryDeviceTable @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile
    private var cached: LaundryTable? = null

    suspend fun load(): LaundryTable {
        cached?.let { return it }
        val base = withContext(Dispatchers.IO) {
            val stored = readStored()
            val fallback = readAssets()
            // 缓存缺 / 缓存比基线旧 → 基线兜底（同版不覆盖，保留缓存里可能的更广数据）
            when {
                stored != null && fallback != null && stored.version >= fallback.version -> stored
                fallback != null -> fallback
                else -> stored
            }
        }
        cached = base ?: LaundryTable.EMPTY
        return cached!!
    }

    /**
     * 拉线上台账；成功落缓存并刷新内存返回 true。
     * 304 / 失败 / 版本回退都返回 false 不动缓存。启动后台跑一次即可，UI 不等它。
     */
    suspend fun refreshFromNetwork(): Boolean {
        val etag = readStoredEtag()
        val result = try {
            LaundryApi.Companion.TABLE.request(etag)
        } catch (error: Exception) {
            record("拉取失败 etag=$etag ${error.javaClass.simpleName}: ${error.message}")
            Log.w(TAG, "台账拉取失败 etag=$etag", error)
            return false
        }
        if (result == null) {
            record("304/坏响应，保留本地")
            return false
        }
        val fresh = LaundryTableParser.parseOrNull(result.text)
        if (fresh == null) {
            record("解析失败 len=${result.text.length}")
            return false
        }
        if (fresh.isEmpty) {
            record("空表 version=${fresh.version}")
            return false
        }
        val local = cached
        if (local != null && fresh.version <= local.version) {
            // 版本没升也落 ETag：下次带条件请求走 304，避免每轮全量重下
            persist(result.text, result.etag)
            record("版本未升 fresh=${fresh.version} local=${local.version}")
            return false
        }
        record("升级成功 version=${fresh.version} devices=${fresh.devices.size} parks=${fresh.parks.size}")
        // 先让本次会话生效再落盘：磁盘异常不该把已经下到手的表一起废掉
        cached = fresh
        runCatching { persist(result.text, result.etag) }
            .onFailure { Log.w(TAG, "台账落盘失败", it) }
        return true
    }

    private fun readStored(): LaundryTable? = runCatching {
        val file = cacheFile()
        if (!file.exists()) return@runCatching null
        LaundryTableParser.parseOrNull(file.readText())
    }.getOrElse { null }

    private fun readAssets(): LaundryTable? = runCatching {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        LaundryTableParser.parseOrNull(text)
    }.getOrElse { null }

    private fun readStoredEtag(): String? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ETAG_KEY, null)
    }.getOrNull()

    private suspend fun persist(text: String, etag: String?) {
        withContext(Dispatchers.IO) {
            val file = cacheFile()
            // 正式包 record() 被 BuildConfig.DEBUG 关掉，不会顺带建目录；
            // 这里必须自己 mkdirs，否则 writeText 抛 ENOENT，整个升级被 runCatching 静默吞掉。
            file.parentFile?.mkdirs()
            file.writeText(text)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ETAG_KEY, etag).apply()
        }
    }

    private fun cacheFile(): File = File(context.filesDir, "laundry/table.json")

    /** vivo 等机型压制 logcat，debug 构建把台账刷新结果落文件方便真机排查。 */
    private fun record(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val file = File(context.filesDir, "laundry/refresh.log")
            file.parentFile?.mkdirs()
            val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            file.appendText("$time $message\n")
        }
    }

    private companion object {
        const val TAG = "LaundryTable"
        const val ASSET_PATH = "laundry/table.json"
        const val PREFS = "laundry_table"
        const val ETAG_KEY = "etag"
    }
}

/** 线上请求结果。 */
data class TableDownload(val text: String, val etag: String?)

/** 台账 JSON 解析（纯 JVM，单测覆盖：完整样本 / 坏 JSON / 缺字段 / 未知类型 / 空表）。 */
object LaundryTableParser {

    fun parseOrNull(text: String): LaundryTable? = try {
        parse(text)
    } catch (_: Exception) {
        null
    }

    fun parse(text: String): LaundryTable {
        val root = JsonParser.parseString(text).asJsonObject
        val version = root.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        val updatedAt = root.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val parksOut = mutableListOf<String>()
        val devices = mutableListOf<LaundryDevice>()
        val parks = root.getAsJsonArray("parks") ?: return LaundryTable(version, updatedAt, parksOut, devices)
        for (i in 0 until parks.size()) {
            val park = parks.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val parkName = park.get("parkName")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            parksOut.add(parkName)
            val parkIndex = park.get("parkId")?.takeIf { it.isJsonPrimitive }?.asInt ?: i
            val list = park.getAsJsonArray("devices") ?: continue
            for (j in 0 until list.size()) {
                val obj = list.get(j).takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val kind = LaundryKind.of(obj.get("deviceType")?.takeIf { it.isJsonPrimitive }?.asString)
                    ?: continue
                val id = obj.get("deviceId")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                devices.add(
                    LaundryDevice(
                        id = id,
                        parkIndex = parkIndex,
                        kind = kind,
                        location = nullableString(obj, "location"),
                        floor = obj.get("floor")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt,
                        slot = nullableString(obj, "slot"),
                        remark = nullableString(obj, "remark"),
                        online = obj.get("online")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                    )
                )
            }
        }
        return LaundryTable(version, updatedAt, parksOut, devices)
    }

    private fun nullableString(obj: com.google.gson.JsonObject, key: String): String? =
        obj.get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString
}
