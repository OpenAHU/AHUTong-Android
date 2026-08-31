package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.ui.components.SecondaryPageScaffold
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ExamViewModel
import com.ahu.ahutong.ui.state.RefreshState
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.ExamDistanceBucket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Exam(
    examViewModel: ExamViewModel = viewModel()
) {
    val behaviorReporter = rememberBehaviorActionReporter()
    LaunchedEffect(Unit) {
        examViewModel.loadExam()
    }
    val exam = examViewModel.data.observeAsState().value?.getOrNull()
    val isLoading by examViewModel.isLoading.collectAsState()
    val errorMessage by examViewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    LaunchedEffect(exam) {
        val now = LocalDateTime.now()
        val hoursUntil = exam.orEmpty().mapNotNull { parseStartTime(it.time) }
            .filter { !it.isBefore(now) }
            .minOfOrNull { java.time.Duration.between(now, it).toHours() }
        behaviorReporter.examDistance(
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
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
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

    if (isRadiantUi) {
        // RadiantUI：标题栏套用统一二级页框架（搜索态也在框架内），刷新简化为图标按钮
        SecondaryPageScaffold(
            title = stringResource(id = R.string.exam),
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExamTitleButton(R.drawable.ic_find, "搜索") { isSearchActive = true }
                    ExamTitleButton(R.drawable.ic_refresh, "刷新") {
                        behaviorReporter.organic(AppActionId.MANUAL_REFRESH_EXAM)
                        examViewModel.loadExam(isRefresh = true)
                    }
                }
            },
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
            )
        ) {
            ExamContent(
                filteredExams = filteredExams,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                isLoading = isLoading,
                horizontal = 0.dp
            )
        }
    } else {
        // Original / Liquid Glass：完整保留原标题栏/搜索栏观感与刷新文本态（冻结不动）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = 0.n1 withNight 100.n1
                        )
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        placeholder = {
                            Text("搜索课程名称…", color = 50.n1 withNight 70.n1)
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = 0.n1 withNight 100.n1,
                            unfocusedTextColor = 0.n1 withNight 100.n1,
                            cursorColor = 90.a1 withNight 90.a1,
                        ),
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = 50.n1 withNight 80.n1
                                    )
                                }
                            }
                        } else null
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.exam),
                        style = MaterialTheme.typography.headlineMedium,
                        color = 0.n1 withNight 100.n1
                    )
                    Row {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = 0.n1 withNight 100.n1
                            )
                        }
                        RefreshButton(examViewModel)
                    }
                }
            }

            ExamContent(
                filteredExams = filteredExams,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                isLoading = isLoading,
                horizontal = 16.dp
            )
        }
    }
}

/** 标题栏右侧圆形图标按钮，样式与统一二级页框架一致。 */
@Composable
private fun ExamTitleButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 考试列表内容（卡片 / 已结束折叠 / 空态 / 加载态），Radiant 与经典模式复用。 */
@Composable
private fun ExamContent(
    filteredExams: List<com.ahu.ahutong.data.model.Exam>,
    isSearchActive: Boolean,
    searchQuery: String,
    isLoading: Boolean?,
    horizontal: Dp
) {
    if (isLoading != true) {
        if (filteredExams.isNotEmpty()) {
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
                modifier = Modifier.padding(horizontal = horizontal),
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
                            .clip(SmoothRoundedCornerShape(12.dp))
                            .background(100.n1 withNight 20.n1)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = 90.a1 withNight 90.a1
                )
                Text(
                    "加载中…",
                    color = 50.n1 withNight 80.n1,
                    fontSize = 14.sp
                )
            }
        }
    }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(100.n1 withNight 20.n1)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
}

@Composable
private fun RefreshButton(examViewModel: ExamViewModel) {
    val behaviorReporter = rememberBehaviorActionReporter()
    val refreshState by examViewModel.refreshState.collectAsState()
    when (refreshState) {
        RefreshState.LOADING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = 90.a1 withNight 90.a1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新中…", fontSize = 13.sp, color = 50.n1 withNight 80.n1)
            }
        }
        RefreshState.UPDATED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("已更新", fontSize = 13.sp, color = Color(0xFF2E7D32))
            }
        }
        RefreshState.IDLE -> {
            IconButton(onClick = {
                behaviorReporter.organic(AppActionId.MANUAL_REFRESH_EXAM)
                examViewModel.loadExam(isRefresh = true)
            }) {
                Icon(Icons.Default.Refresh, "刷新", tint = 0.n1 withNight 100.n1)
            }
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