package com.ahu.ahutong.electricity

import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Settings and the disposable forecast marker are separate, private, account-scoped files. */
internal class ElectricityAlertFiles(private val directory: File) {
    private val gson = Gson()

    @Synchronized
    fun configuration(profile: String): ElectricityAlertConfiguration =
        read(file(profile, "settings"), ElectricityAlertConfiguration::class.java)
            ?: ElectricityAlertConfiguration()

    @Synchronized
    fun saveConfiguration(profile: String, configuration: ElectricityAlertConfiguration) =
        write(file(profile, "settings"), configuration)

    @Synchronized
    fun marker(profile: String): ElectricityAlertMarker =
        read(file(profile, "forecast"), ElectricityAlertMarker::class.java) ?: ElectricityAlertMarker()

    @Synchronized
    fun saveMarker(profile: String, marker: ElectricityAlertMarker) =
        write(file(profile, "forecast"), marker)

    private fun file(profile: String, kind: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(profile.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash-$kind.json")
    }

    private fun <T> read(file: File, type: Class<T>): T? {
        if (!file.exists()) return null
        return runCatching { gson.fromJson(file.readText(), type) }.getOrNull()
    }

    private fun write(file: File, value: Any) {
        check(directory.isDirectory || directory.mkdirs()) { "无法保存电费预警设置" }
        val pending = File(directory, "${file.name}.tmp")
        pending.outputStream().use { output ->
            output.write(gson.toJson(value).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

internal data class ElectricityAlertMarker(
    val rooms: Map<String, ElectricityRoomForecast> = emptyMap(),
    val lastPromptDay: String? = null
)

internal data class ElectricityRoomForecast(
    val thresholdDays: Int,
    val lastAttemptDay: String,
    val sampleDay: String? = null,
    val remainingKwh: Double? = null,
    val dailyKwh: Double? = null,
    val nextCheckDay: String? = null,
    val zeroDay: String? = null,
    val algorithmVersion: Int = 0,
    val riskKwhByDays: List<Double>? = null,
    val method: String? = null
)
