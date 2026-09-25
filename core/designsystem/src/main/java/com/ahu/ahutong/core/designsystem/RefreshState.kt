package com.ahu.ahutong.core.designsystem

/** 手动刷新三态：空闲 / 刷新中 / 已更新（用于 AppRefreshButton 等刷新入口）。 */
enum class RefreshState { IDLE, LOADING, UPDATED }
