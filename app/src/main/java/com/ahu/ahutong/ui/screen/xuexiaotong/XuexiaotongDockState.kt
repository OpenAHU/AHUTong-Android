package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class XuexiaotongSubTab { SCHEDULE, COURSE }

// 学习通日历的两个子页（日程/课程）由底部导航栏轮换切换，
// 该状态被 XuexiaotongScreen 与 BottomNavBar 共享，保证重新进入后停留在上次子页。
object XuexiaotongDockState {
    var tab by mutableStateOf(XuexiaotongSubTab.SCHEDULE)

    fun toggle() {
        tab = if (tab == XuexiaotongSubTab.SCHEDULE) XuexiaotongSubTab.COURSE
        else XuexiaotongSubTab.SCHEDULE
    }
}