package com.ahu.ahutong.data.mock

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.base.BaseDataSource
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import okhttp3.ResponseBody
import retrofit2.Response

class MockDataSource : BaseDataSource {
    override suspend fun getSchedule(
        schoolYear: String,
        schoolTerm: String
    ): AhuResult<List<Course>> = unavailable()

    override suspend fun getSchedule(): AhuResult<List<Course>> = unavailable()

    override suspend fun getNextSchedule(): AhuResult<List<Course>> = unavailable()

    override suspend fun getScheduleSemesters(): AhuResult<List<ScheduleSemester>> = unavailable()

    override suspend fun getSemesterSchedule(semesterId: Int): AhuResult<List<Course>> = unavailable()

    override suspend fun getGrade(): AhuResult<Grade> = unavailable()

    override suspend fun getGpaRankFromHtml(studentId: String): AhuResult<GpaRankInfo> = unavailable()

    override suspend fun getAllCampus(): AhuResult<AllCampus> = unavailable()

    override suspend fun getAllLostFoundType(): AhuResult<AllLostFoundType> = unavailable()

    override suspend fun getLostFoundList(
        pageNo: Int,
        pageSize: Int,
        state: Int
    ): AhuResult<LostFoundResponse> = unavailable()

    override suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AhuResult<Any> = unavailable()

    override suspend fun deleteLostFound(id: String): AhuResult<Any> = unavailable()

    override suspend fun getCardMoney(): AhuResult<Card> = unavailable()

    override suspend fun getBathRooms(): AhuResult<List<BathRoom>> = unavailable()

    override suspend fun getExamInfo(
        studentID: String,
        studentName: String
    ): AhuResult<List<Exam>> = unavailable()

    override suspend fun getBathroomTelInfo(
        bathroom: String,
        tel: String
    ): AhuResult<BathroomTelInfo> = unavailable()

    override suspend fun getCardInfo(): AhuResult<CardInfo> = unavailable()

    override suspend fun getBillPage(
        page: Int,
        size: Int,
        timeFrom: String?,
        timeTo: String?,
        type: Int?
    ): AhuResult<TurnoverPage> = unavailable()

    override suspend fun getBillSummary(timeFrom: String, timeTo: String): AhuResult<TurnoverCount> =
        unavailable()

    override suspend fun getOrderThirdData(
        request: RequestBody
    ): AhuResult<Response<ResponseBody>> = unavailable()

    override suspend fun pay(
        request: RequestBody
    ): AhuResult<Response<ResponseBody>> = unavailable()

    override suspend fun getSchoolCalendar(): AhuResult<Response<ResponseBody>> = unavailable()

    override suspend fun getSchoolCalendarYears(): AhuResult<SchoolCalendarYearsResponse> = unavailable()

    override suspend fun getSchoolCalendar(year: String): AhuResult<Response<ResponseBody>> = unavailable()

    private fun <T> unavailable(): AhuResult<T> =
        AhuResult.Failure(AhuError.Server(-1, "Mock data source is debug-only."))
}

object MockCampusData {
    fun buildings(campusId: Int): List<GetBuildingsResponseItem> = emptyList()

    fun freeRooms(
        campusId: Int,
        buildingIds: List<Int>
    ): List<FreeRoom> = emptyList()
}
