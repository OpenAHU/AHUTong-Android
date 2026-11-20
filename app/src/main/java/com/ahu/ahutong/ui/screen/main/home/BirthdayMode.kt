package com.ahu.ahutong.ui.screen.main.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import java.time.LocalDate

/**
 * 生日祝福态：只比较学籍档案里的公历月-日，判断和状态都留在本机。
 * 冷启动默认进入祝福态；用户主动退出后，本次进程内不再自动进入。
 */
/** 生日文案专用字体（已按四句文案做字形裁剪）。 */
internal val BirthdayFontFamily = FontFamily(Font(R.font.birthday_freedom))

internal object BirthdayMode {
    private const val TAG = "BirthdayMode"
    private var initialized = false
    private var lastDay: LocalDate? = null
    private var debugForced = false
    private var userExited = false

    var isBirthday by mutableStateOf(false)
        private set
    var celebrating by mutableStateOf(false)
        private set

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        val today = DebugClock.nowLocalDate()
        lastDay = today
        isBirthday = debugForced || matchesBirthday(today)
        celebrating = isBirthday
        if (isBirthday) Log.i(TAG, "birthday mode active today=$today")
    }

    /** 跨天时重新判断；进入生日当天自动回到祝福态。 */
    fun refresh(today: LocalDate) {
        if (lastDay == today) return
        lastDay = today
        val birthday = debugForced || matchesBirthday(today)
        if (birthday != isBirthday) Log.i(TAG, "birthday day boundary -> $birthday")
        isBirthday = birthday
        celebrating = birthday
    }

    /** 学籍缓存补拉完成后重新判断，但不覆盖用户已主动退出的状态。 */
    fun reload() {
        lastDay = DebugClock.nowLocalDate()
        isBirthday = debugForced || matchesBirthday(lastDay!!)
        if (isBirthday && !userExited) celebrating = true
    }

    fun enter() {
        if (isBirthday && !celebrating) {
            celebrating = true
            Log.i(TAG, "enter celebration")
        }
    }

    fun exit() {
        if (celebrating) {
            celebrating = false
            Log.i(TAG, "exit celebration")
        }
    }

    fun setDebugForced(forced: Boolean) {
        debugForced = forced
        lastDay = null
        userExited = false
        refresh(DebugClock.nowLocalDate())
        if (forced) celebrating = true
        Log.i(TAG, "debug forced birthday=$forced")
    }

    fun isDebugForced(): Boolean = debugForced

    private fun matchesBirthday(today: LocalDate): Boolean {
        val raw = runCatching { AHUCache.getStudentInfo()?.birthDate }.getOrNull() ?: return false
        val match = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(raw) ?: return false
        val month = match.groupValues[2].toIntOrNull() ?: return false
        val day = match.groupValues[3].toIntOrNull() ?: return false
        if (month == 2 && day == 29 && !today.isLeapYear) {
            return today.monthValue == 2 && today.dayOfMonth == 28
        }
        return today.monthValue == month && today.dayOfMonth == day
    }
}
