package com.ahu.ahutong.core.common

/**
 * Stable-prep 分支的必吃榜用户面开关。
 *
 * 必吃榜完整实现只留在 Best-Bites 分支；stable-prep 合入 dev/master 时保持关闭：
 * 不弹共建弹窗、不显示主页认窗口卡片、不出现小工具入口、不请求服务器。
 * 代码与页面保留，待 HTTPS/备案完成后可整体启用。
 */
object CanteenFeature {
    const val ENABLED: Boolean = false
}
