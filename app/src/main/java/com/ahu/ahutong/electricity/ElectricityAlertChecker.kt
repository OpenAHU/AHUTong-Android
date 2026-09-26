package com.ahu.ahutong.electricity

import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.recharge.ElectricityUsagePredictor
import com.ahu.ahutong.data.recharge.ElectricityUsageSource
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One automatic balance/history cycle per room/day, including failed attempts and process restarts. */
internal class ElectricityAlertChecker(
    private val files: ElectricityAlertFiles,
    private val source: ElectricityUsageSource,
    private val profileIsCurrent: (String) -> Boolean
) {
    private val mutex = Mutex()

    suspend fun check(
        profile: String,
        configuration: ElectricityAlertConfiguration,
        today: LocalDate
    ): List<ElectricityAlertRoom> = mutex.withLock {
        if (!configuration.enabled || !profileIsCurrent(profile)) return@withLock emptyList()
        var marker = files.marker(profile)
        val configuredKeys = configuration.rooms.map { it.key }.toSet()
        // Removing and re-adding a room must not erase its already-consumed daily request slot.
        marker = marker.copy(rooms = marker.rooms.filter { (key, forecast) ->
            key in configuredKeys || forecast.lastAttemptDay == today.toString()
        })
        for (room in configuration.rooms) {
            if (!profileIsCurrent(profile)) return@withLock emptyList()
            val saved = marker.rooms[room.key]
            val due = saved == null || saved.algorithmVersion != ElectricityUsagePredictor.ALGORITHM_VERSION ||
                saved.thresholdDays != room.thresholdDays ||
                saved.nextCheckDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.let { !today.isBefore(it) } != false
            if (!due || saved?.lastAttemptDay == today.toString()) continue

            // Persist before sending either request. Killing the app or a network error cannot
            // turn navigation/relogin into repeated automatic electricity requests on the same day.
            val attempted = ElectricityRoomForecast(room.thresholdDays, today.toString(),
                algorithmVersion = ElectricityUsagePredictor.ALGORITHM_VERSION)
            marker = marker.copy(rooms = marker.rooms + (room.key to attempted))
            files.saveMarker(profile, marker)
            val snapshot = try {
                source.snapshot(room.selection.controller ?: ElectricityController.C, room.selection, today)
                    .valueOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: continue
            if (!profileIsCurrent(profile)) return@withLock emptyList()
            val forecast = runCatching { ElectricityUsagePredictor.predict(snapshot, room.thresholdDays) }
                .getOrNull() ?: continue
            val completed = attempted.copy(
                sampleDay = today.toString(), remainingKwh = snapshot.remainingKwh,
                dailyKwh = forecast.dailyKwh, nextCheckDay = forecast.nextCheckDate.toString(),
                zeroDay = forecast.zeroDate?.toString(), riskKwhByDays = forecast.riskKwhByDays,
                method = forecast.method
            )
            marker = marker.copy(rooms = marker.rooms + (room.key to completed))
            files.saveMarker(profile, marker)
        }
        if (marker.lastPromptDay == today.toString()) return@withLock emptyList()
        configuration.rooms.filter { room ->
            val saved = marker.rooms[room.key]
            val risk = saved?.riskKwhByDays?.getOrNull(room.thresholdDays - 1)
                ?: saved?.dailyKwh?.times(room.thresholdDays)
            saved?.sampleDay == today.toString() && saved.remainingKwh != null &&
                (saved.remainingKwh <= 0.0 || risk != null && risk > 0.0 && saved.remainingKwh <= risk)
        }
    }

    suspend fun claimPrompt(profile: String, today: LocalDate): Boolean = mutex.withLock {
        if (!profileIsCurrent(profile)) return@withLock false
        val marker = files.marker(profile)
        if (marker.lastPromptDay == today.toString()) return@withLock false
        files.saveMarker(profile, marker.copy(lastPromptDay = today.toString()))
        true
    }
}
