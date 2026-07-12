package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.CrawlerDataSource
import com.ahu.ahutong.data.model.Course
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrawlerScheduleSource @Inject constructor() : ScheduleCrawlerSource {
    private val crawler = CrawlerDataSource()

    override suspend fun fetchSchedule(): AppResult<List<Course>> {
        return try {
            val response = crawler.getSchedule()
            if (response.isSuccessful && response.data != null) {
                AppResult.success(response.data)
            } else {
                AppResult.error(response.msg ?: "获取课表失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取课表失败", t)
        }
    }

    override suspend fun fetchNextSchedule(): AppResult<List<Course>> {
        return try {
            val response = crawler.getNextSchedule()
            if (response.isSuccessful && response.data != null) {
                AppResult.success(response.data)
            } else {
                AppResult.error(response.msg ?: "获取下学期课表失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取下学期课表失败", t)
        }
    }
}
