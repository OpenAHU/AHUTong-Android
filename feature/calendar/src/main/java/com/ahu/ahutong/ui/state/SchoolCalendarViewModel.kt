package com.ahu.ahutong.ui.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.calendar.SchoolCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SchoolCalendarViewModel @Inject constructor(
    private val schoolCalendarRepository: SchoolCalendarRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    var calendarFile by mutableStateOf<File?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun fetchCalendar(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            isLoading = true
            progress = 0f
            errorMessage = null
            when (
                val result = schoolCalendarRepository.getCalendarImage(
                    context = appContext,
                    forceRefresh = forceRefresh,
                    onProgress = { progress = it },
                )
            ) {
                is AppResult.Success -> {
                    calendarFile = result.data
                    progress = 1f
                }
                is AppResult.Error -> {
                    errorMessage = result.message
                    calendarFile = null
                }
            }
            isLoading = false
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
