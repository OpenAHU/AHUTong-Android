package com.ahu.ahutong.personalization.journey

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect

object JourneyGoalCatalog {
    const val OUTPUT_SCHEMA_VERSION = 2
    const val OTHER_OUTPUT_ID = "OTHER"
    const val NONE_OUTPUT_ID = "NONE"

    val shellActions: Set<AppActionId> = setOf(
        AppActionId.OPEN_HOME,
        AppActionId.OPEN_TOOLS,
        AppActionId.OPEN_SETTINGS,
        AppActionId.OPEN_PREFERENCES,
        AppActionId.EDIT_HOME
    )

    val terminalActions: List<AppActionId> = AppActionCatalog.specs
        .filter { spec ->
            spec.predictable && spec.id !in shellActions && spec.sideEffect != SideEffect.TRANSACTION
        }
        .map { it.id }

    val outputIds: List<String> = terminalActions.map(AppActionId::stableId) + OTHER_OUTPUT_ID + NONE_OUTPUT_ID
    val outputIndex: Map<String, Int> = outputIds.withIndex().associate { it.value to it.index }

    fun isSafeTerminal(action: AppActionId): Boolean = action in terminalActions

    fun isImmediateMilestone(action: AppActionId): Boolean = action in setOf(
        AppActionId.MANUAL_REFRESH_SCHEDULE,
        AppActionId.MANUAL_REFRESH_EXAM,
        AppActionId.MANUAL_REFRESH_GRADE,
        AppActionId.MANUAL_REFRESH_REPOSITORY,
        AppActionId.RETRY_GRADE,
        AppActionId.RETRY_EXAM,
        AppActionId.RETRY_REPOSITORY,
        AppActionId.OPEN_COURSE_DETAIL,
        AppActionId.OPEN_REPOSITORY_ITEM,
        AppActionId.DOWNLOAD_REPOSITORY_ITEM
    )
}
