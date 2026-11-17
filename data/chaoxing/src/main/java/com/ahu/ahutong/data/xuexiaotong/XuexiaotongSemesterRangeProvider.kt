package com.ahu.ahutong.data.xuexiaotong

/**
 * 提供教务口径的当前学期区间（毫秒，首尾均含）。
 *
 * 接口放在数据层，具体实现留在 :app 的非 UI 数据包，避免 UI 层直接依赖教务 API。
 */
interface XuexiaotongSemesterRangeProvider {
    suspend fun currentSemesterRange(): LongRange?
}
