package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.feature.exam.R
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.components.AhuErrorToastEffect
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuListGroup
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ExamViewModel
import com.ahu.ahutong.ui.state.RefreshState
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Exam(
    examViewModel: ExamViewModel = hiltViewModel(),
    mockRefreshRevision: Long = 0L,
) {
    LaunchedEffect(Unit) {
        examViewModel.loadExam()
    }
    val exam = examViewModel.data.observeAsState().value?.getOrNull()
    val isLoading by examViewModel.isLoading.collectAsState()
    val errorMessage by examViewModel.errorMessage.collectAsState()

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && examViewModel.isMockMode()) {
            examViewModel.loadExam(isRefresh = true)
        }
    }

    AhuErrorToastEffect(errorMessage) {
        examViewModel.errorMessage.value = null
    }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredExams = if (isSearchActive && searchQuery.isNotBlank()) {
        exam?.filter { it.course.contains(searchQuery, ignoreCase = true) } ?: emptyList()
    } else {
        exam.orEmpty()
    }

    AhuScreen {
        // 标题栏 / 搜索栏
        if (isSearchActive) {
            // 搜索模式：回退按钮 + TextField + 清除按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isSearchActive = false
                    searchQuery = ""
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_course_name)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AhuColors.onSurface,
                        unfocusedTextColor = AhuColors.onSurface,
                        cursorColor = 90.a1 withNight 90.a1,
                    ),
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    } else null
                )
            }
        } else {
            // 正常模式：标题 + 搜索/刷新按钮
            AhuPageHeader(
                title = stringResource(id = R.string.exam),
                actions = {
                    AhuHeaderIconButton(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        onClick = { isSearchActive = true },
                    )
                    RefreshButton(examViewModel)
                },
            )
        }

        if (isLoading != true) {
            if (!filteredExams.isNullOrEmpty()) {
                val sortedExams = filteredExams.sortedWith(
                    compareBy(
                        { calcTime(it.time) },
                        { parseStartTime(it.time) ?: LocalDateTime.MAX }
                    )
                )
                AhuListGroup {
                    sortedExams.forEach {
                        AhuCard(
                            cornerRadius = AhuDimens.ListItemCorner,
                            onClick = {},
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = it.course,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val isFinished = calcTime(it.time)
                                val cardColor: Color = when (isFinished) {
                                    0 -> Color(0xFFFFC107)
                                    1 -> Color(0xFF4CAF50)
                                    2 -> Color.Gray
                                    else -> Color.Red
                                }
                                Box(
                                    modifier = Modifier
                                        .background(color = cardColor, shape = SmoothRoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (isFinished) {
                                            0 -> stringResource(R.string.status_in_progress)
                                            1 -> stringResource(R.string.status_not_started)
                                            2 -> stringResource(R.string.status_finished)
                                            else -> stringResource(R.string.status_time_parse_error)
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.exam_time, it.time),
                                color = 30.n1 withNight 90.n1,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(
                                    R.string.exam_location_seat,
                                    it.location,
                                    it.seatNum,
                                ),
                                color = 50.n1 withNight 80.n1,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = when {
                        isSearchActive && searchQuery.isNotBlank() ->
                            stringResource(R.string.no_exam_matching, searchQuery)
                        else -> stringResource(R.string.no_exams)
                    },
                    modifier = Modifier.padding(AhuDimens.TitleHorizontal),
                    style = MaterialTheme.typography.titleLarge,
                    color = AhuColors.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(color = AhuColors.primaryAction)
                Text(stringResource(R.string.loading))
            }
        }
    }
}

@Composable
private fun RefreshButton(examViewModel: ExamViewModel) {
    val refreshState by examViewModel.refreshState.collectAsState()
    when (refreshState) {
        RefreshState.LOADING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.refreshing),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RefreshState.UPDATED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.width(20.dp).height(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.updated),
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }
        RefreshState.IDLE -> {
            AhuHeaderIconButton(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = { examViewModel.loadExam(isRefresh = true) },
            )
        }
    }
}


fun calcTime(time: String): Int {
    val now = LocalDateTime.now()
    val parts = time.split(" ")
    if (parts.size != 2 || !parts[1].contains("~")) return 3
    val datePart = parts[0]
    val timeParts = parts[1].split("~")
    if (timeParts.size != 2) return 3
    val startDateTimeStr = "$datePart ${timeParts[0]}"
    val endDateTimeStr = "$datePart ${timeParts[1]}"
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val startTime = LocalDateTime.parse(startDateTimeStr, formatter)
    val endTime = LocalDateTime.parse(endDateTimeStr, formatter)
    return when {
        now.isBefore(startTime) -> 1
        now.isAfter(endTime) -> 2
        else -> 0
    }
}

private fun parseStartTime(time: String): LocalDateTime? {
    val parts = time.split(" ")
    if (parts.size != 2 || !parts[1].contains("~")) return null
    val datePart = parts[0]
    val timeParts = parts[1].split("~")
    if (timeParts.size != 2) return null
    val startDateTimeStr = "$datePart ${timeParts[0]}"
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return runCatching { LocalDateTime.parse(startDateTimeStr, formatter) }.getOrNull()
}
