package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 导览与作业详情弹窗之间的最小握手：是否已打开、是否请求关闭。 */
object XuexiaotongTourState {
    var workDetailOpen by mutableStateOf(false)
    var closeWorkDetailRequested by mutableStateOf(false)
}
