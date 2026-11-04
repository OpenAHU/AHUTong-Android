package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean

/**
 * AI 能力注册表的唯一装配点：只读能力 + 导航型动作能力。
 *
 * 这里不出现任何页面、ViewModel 或执行逻辑；删掉 AI 入口后，App 行为不变。
 */
object AssistantCapabilities {

    fun all(
        config: () -> ScheduleConfigBean?,
        cachedSchedule: () -> List<Course>?,
    ): List<AssistantCapability> = listOf(
        GetScheduleCapability(config = config, cached = cachedSchedule),
        FreeClassroomCapability(),
        GetGradesCapability(),
        GetGpaRankCapability(),
        GetExamsCapability(),
        CardBalanceCapability(),
        GetBillsCapability(),
        GetElectricityCapability(),
        GetElectricityConditionsCapability(),
        GetAttendanceCapability(),
        GetProgramCompletionCapability(),
        GetEvaluationTasksCapability(),
        GetCanteenRankingCapability(),
        GetLostFoundCapability(),
        GetNoticesCapability(),
        GetMailCapability(),
        GetPhoneBookCapability(),
        GetAssignmentsCapability(),
        GetCourseTasksCapability(),
        GetWeatherCapability(),
        WebSearchCapability(),
        RechargeCardCapability(),
        RechargeElectricityCapability(),
        RechargeBathroomCapability(),
        RechargeNetworkCapability(),
        SendMailCapability(),
        PublishLostFoundCapability(),
        DeleteLostFoundCapability(),
        SwitchElectricityConditionCapability(),
        SubmitEvaluationCapability(),
    ) + navigateCapabilities()
}
