package com.ahu.ahutong.data.di

import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.exam.ExamRepository
import com.ahu.ahutong.data.grade.GradeRepository
import com.ahu.ahutong.data.schedule.ScheduleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for object facades (AHURepository) and non-Hilt call sites.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataEntryPoint {
    fun scheduleRepository(): ScheduleRepository
    fun authRepository(): AuthRepository
    fun gradeRepository(): GradeRepository
    fun examRepository(): ExamRepository
}
