package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.feature.grade.R
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.AppRefreshButton
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ExamViewModel
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import com.ahu.ahutong.core.designsystem.R as DesignSystemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Exam(
    examViewModel: ExamViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        examViewModel.loadExam()
    }
    val exam = examViewModel.data.observeAsState().value?.valueOrNull()
    val isLoading by examViewModel.isLoading.collectAsState()
    val errorMessage by examViewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val mockRefreshRevision by examViewModel.mockRefreshRevisions.collectAsState(0L)

    LaunchedEffect(exam) {
        val now = LocalDateTime.now()
        val hoursUntil = exam.orEmpty().mapNotNull { parseStartTime(it.time) }
            .filter { !it.isBefore(now) }
            .minOfOrNull { java.time.Duration.between(now, it).toHours() }
        examViewModel.onExamDistance(
            when {
                exam.isNullOrEmpty() -> ExamDistanceBucket.NONE
                hoursUntil == null -> ExamDistanceBucket.NONE
                hoursUntil <= 24 -> ExamDistanceBucket.WITHIN_ONE_DAY
                hoursUntil <= 72 -> ExamDistanceBucket.WITHIN_THREE_DAYS
                hoursUntil <= 168 -> ExamDistanceBucket.WITHIN_SEVEN_DAYS
                else -> ExamDistanceBucket.LATER
            }
        )
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && examViewModel.usesMockData) {
            examViewModel.loadExam(isRefresh = true)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            examViewModel.errorMessage.value = null
        }
    }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredExams = if (isSearchActive && searchQuery.isNotBlank()) {
        exam?.filter { it.course.contains(searchQuery, ignoreCase = true) } ?: emptyList()
    } else {
        exam.orEmpty()
    }

    val pageContent: @Composable ColumnScope.() -> Unit = {
        if (isLoading != true) {
            if (!filteredExams.isNullOrEmpty()) {
                val sortedExams = filteredExams.sortedWith(
                    compareBy(
                        { calcTime(it.time) },
                        { parseStartTime(it.time) ?: LocalDateTime.MAX }
                    )
                )
                // Split into active and finished
                val activeExams = sortedExams.filter { calcTime(it.time) != 2 }
                val finishedExams = sortedExams.filter { calcTime(it.time) == 2 }
                var showFinished by rememberSaveable { mutableStateOf(false) }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active exams — always visible
                    activeExams.forEach { examItem ->
                        ExamCard(examItem = examItem, status = calcTime(examItem.time))
                    }

                    // Finished exams — collapsible
                    if (finishedExams.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appLiquidGlassSurface(
                                    shape = SmoothRoundedCornerShape(12.dp),
                                    fallbackColor = 100.n1 withNight 20.n1,
                                    level = LiquidGlassSurfaceLevel.Control
                                )
                                .clickable { showFinished = !showFinished }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已结束 (${finishedExams.size})",
                                color = 30.n1 withNight 90.n1,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (showFinished) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showFinished) "收起" else "展开",
                                tint = 50.n1 withNight 70.n1
                            )
                        }

                        AnimatedVisibility(
                            visible = showFinished,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                finishedExams.forEach { examItem ->
                                    ExamCard(examItem = examItem, status = 2)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isSearchActive && searchQuery.isNotBlank() -> "未找到包含「${searchQuery}」的考试"
                            else -> "目前没有任何考试"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = 50.n1 withNight 80.n1
                    )
                }
            }
        } else {
            AppStateCard.Loading(
                message = "加载中…",
                modifier = Modifier.padding(vertical = 88.dp)
            )
        }
    }

    AppPageScaffold(
        title = stringResource(id = R.string.exam),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        search = SecondarySearchState(
            query = searchQuery,
            visible = isSearchActive,
            placeholder = "搜索课程名称…",
            onQueryChange = { searchQuery = it },
            onClose = {
                isSearchActive = false
                searchQuery = ""
            },
            onSubmit = {}
        ),
        trailingContent = {
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_find,
                contentDescription = "搜索",
                onClick = { isSearchActive = true }
            )
            val refreshState by examViewModel.refreshState.collectAsState()
            AppRefreshButton(
                state = refreshState,
                contentDescription = "刷新考试",
                onClick = {
                    examViewModel.loadExam(isRefresh = true)
                }
            )
        },
        bottomPadding = 48.dp,
        content = pageContent
    )
}

/** "磬苑校区-博学楼-博学楼A101" → "磬苑校区 博学楼A101" */
private fun shortenLocation(raw: String): String {
    val parts = raw.split("-")
    return if (parts.size >= 3) "${parts.first()} ${parts.last()}" else raw
}

@Composable
private fun ExamCard(
    examItem: com.ahu.ahutong.data.model.Exam,
    status: Int
) {
    val (statusText, statusIcon) = when (status) {
        0 -> "进行中" to Icons.Filled.AccessTime
        1 -> "未开始" to Icons.Outlined.Schedule
        2 -> "已结束" to Icons.Filled.CheckCircle
        else -> "时间错误" to Icons.Filled.Close
    }
    val badgeBg = when (status) {
        0 -> 95.a1 withNight 30.a1
        1 -> Color(0xFF2E7D32)
        2 -> 30.n1 withNight 70.n1
        else -> Color(0xFFC62828)
    }

    val cardContent: @Composable ColumnScope.() -> Unit = {
        // Course name + status badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = examItem.course,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = 0.n1 withNight 100.n1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        // Time
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccessTime, null, tint = 50.n1 withNight 70.n1, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(examItem.time, color = 30.n1 withNight 90.n1, style = MaterialTheme.typography.bodyMedium)
        }
        // Location (shortened)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, null, tint = 50.n1 withNight 70.n1, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(shortenLocation(examItem.location), color = 50.n1 withNight 80.n1, style = MaterialTheme.typography.bodyMedium)
        }
        // Seat number — separate row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EventSeat, null, tint = 50.n1 withNight 70.n1, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("座位号：${examItem.seatNum}", color = 50.n1 withNight 80.n1, style = MaterialTheme.typography.bodyMedium)
        }
    }

    AppSectionCard(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = cardContent
    )
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
