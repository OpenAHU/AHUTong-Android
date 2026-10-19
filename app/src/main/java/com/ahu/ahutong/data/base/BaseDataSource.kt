package com.ahu.ahutong.data.base

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
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

/**
 * @Author: Sink
 * @Date: 2021/7/31-下午8:40
 * @Email: 468766131@qq.com
 */
interface BaseDataSource {

    /**
     * getSchedule
     * @param schoolYear String 2020-2021
     * @param schoolTerm String 1,2
     * @return AhuResult<List<Course>>
     */
    suspend fun getSchedule(schoolYear: String, schoolTerm: String): AhuResult<List<Course>>

    /**
     * getSchedule (auto get schedule of this semester)
     */
    suspend fun getSchedule() : AhuResult<List<Course>>

    /**
     * getSchedule (auto get schedule of next semester)
     */
    suspend fun getNextSchedule() : AhuResult<List<Course>>

    suspend fun getScheduleSemesters(): AhuResult<List<ScheduleSemester>> =
        AhuResult.Failure(AhuError.Server(-1, "当前数据源不支持学期目录"))

    suspend fun getSemesterSchedule(semesterId: Int): AhuResult<List<Course>> =
        AhuResult.Failure(AhuError.Server(-1, "当前数据源不支持按学期浏览课表"))

    suspend fun getGrade(): AhuResult<Grade>

    suspend fun getGpaRankFromHtml(studentId: String): AhuResult<GpaRankInfo>

    suspend fun getAllCampus(): AhuResult<AllCampus>
    suspend fun getAllLostFoundType(): AhuResult<AllLostFoundType>

    suspend fun getLostFoundList(
        pageNo: Int = 1,
        pageSize: Int = 20,
        state: Int = 1
    ): AhuResult<LostFoundResponse>

    suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AhuResult<Any>

    suspend fun deleteLostFound(
        id: String
    ): AhuResult<Any>

    suspend fun getCardMoney(): AhuResult<Card>

    suspend fun getBathRooms(): AhuResult<List<BathRoom>>

    /**
     * get exam info
     */
    suspend fun getExamInfo(studentID: String, studentName: String): AhuResult<List<Exam>>


    /**
     * get account info by tel and bathroom
     */
    suspend fun getBathroomTelInfo(bathroom:String,tel: String): AhuResult<BathroomTelInfo>


    /**
     * get card info for charge
     */
    suspend fun getCardInfo(): AhuResult<CardInfo>

    /** 账单流水分页（timeFrom/timeTo 传 null = 当月；type: 2=消费 1=充值 null=全部）。 */
    suspend fun getBillPage(
        page: Int,
        size: Int,
        timeFrom: String? = null,
        timeTo: String? = null,
        type: Int? = null
    ): AhuResult<TurnoverPage>

    /** 账单收支汇总（金额单位：分）。 */
    suspend fun getBillSummary(timeFrom: String, timeTo: String): AhuResult<TurnoverCount>


    /**
     * gets third-party order data before executing payment
     */

    suspend fun getOrderThirdData(request : RequestBody): AhuResult<Response<ResponseBody>>

    suspend fun pay(request : RequestBody): AhuResult<Response<ResponseBody>>

    suspend fun getSchoolCalendar(): AhuResult<Response<ResponseBody>>

    suspend fun getSchoolCalendarYears(): AhuResult<SchoolCalendarYearsResponse>

    suspend fun getSchoolCalendar(year: String): AhuResult<Response<ResponseBody>>

}
