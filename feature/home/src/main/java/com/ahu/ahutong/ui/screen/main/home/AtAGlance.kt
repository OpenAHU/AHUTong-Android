package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.feature.home.R
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    nowDate: Date,
    navController: NavHostController,
    enabled: Boolean = true
) {
    val currentCourse = todayCourses.find {
        currentMinutes in ScheduleViewModel.getCourseTimeRangeInMinutes(it)
    }
    val currentCourseIndex = todayCourses.indexOfFirst {
        val range = ScheduleViewModel.getCourseTimeRangeInMinutes(it)
        if (currentMinutes in range) {
            true
        } else {
            currentMinutes < range.first
        }
    }.takeIf { it != -1 } ?: todayCourses.lastIndex
    val hasRemainingCourses = if (todayCourses.isNotEmpty()) {
        currentMinutes <= ScheduleViewModel.getCourseTimeRangeInMinutes(todayCourses.last()).last
    } else {
        false
    }
    val date = SimpleDateFormat("MM-dd / EE", Locale.CHINA).format(nowDate)
    Column(
        modifier = Modifier.padding(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp, 8.dp, AhuDimens.ContentHorizontal, 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date,
                color = AhuColors.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(AhuDimens.CardCorner))
                .then(
                    if (enabled) {
                        Modifier.clickable { navController.navigate("schedule") }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when {
                    currentCourse != null -> stringResource(R.string.in_class)
                    hasRemainingCourses -> stringResource(R.string.next_class_is)
                    else -> stringResource(R.string.today_courses)
                },
                color = AhuColors.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = when {
                    currentCourse != null -> currentCourse.name
                    hasRemainingCourses -> todayCourses[currentCourseIndex].name
                    else -> stringResource(R.string.all_courses_done)
                },
                modifier = if (currentCourse != null || hasRemainingCourses) {
                    Modifier
                        .composed {
                            val color = AhuColors.primaryAction
                            drawBehind {
                                drawLine(
                                    color = color,
                                    start = Offset.Zero,
                                    end = Offset(0f, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                        }
                        .padding(start = 8.dp)
                } else {
                    Modifier
                },
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AhuColors.onSurface,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = when {
                    currentCourse != null -> {
                        val duration =
                            ScheduleViewModel.getCourseTimeRangeInMinutes(currentCourse).last - currentMinutes
                        stringResource(
                            R.string.time_until_class_end,
                            formatDuration(duration),
                        )
                    }

                    hasRemainingCourses -> {
                        val duration =
                            ScheduleViewModel.getCourseTimeRangeInMinutes(
                                todayCourses[currentCourseIndex]
                            ).first - currentMinutes
                        stringResource(
                            R.string.time_until_class_start,
                            formatDuration(duration),
                            todayCourses[currentCourseIndex].location.orEmpty(),
                        )
                    }

                    else -> stringResource(R.string.prepare_own_schedule)
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun formatDuration(duration: Int): String {
    return when {
        duration % 60 == 0 -> stringResource(R.string.hours_exact, duration / 60)
        duration > 60 -> stringResource(
            R.string.hours_and_minutes,
            duration / 60,
            duration % 60,
        )
        else -> stringResource(R.string.minutes_only, duration)
    }
}
