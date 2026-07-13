package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.feature.schedule.R
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.theme.AhuColors
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit
) {

   val courses = course.weekIndexes.last() - course.weekIndexes.first()

    val numToChinese = mapOf(
        1 to stringResource(R.string.weekday_num_1),
        2 to stringResource(R.string.weekday_num_2),
        3 to stringResource(R.string.weekday_num_3),
        4 to stringResource(R.string.weekday_num_4),
        5 to stringResource(R.string.weekday_num_5),
        6 to stringResource(R.string.weekday_num_6),
        7 to stringResource(R.string.weekday_num_7)
    )
    val weekPart = when {
        courses == course.weekIndexes.size - 1 -> stringResource(
            R.string.week_range,
            course.weekIndexes.first(),
            course.weekIndexes.last()
        )
        courses == (course.weekIndexes.size - 1) * 2 && course.weekIndexes.first() % 2 == 0 ->
            stringResource(
                R.string.week_range_even,
                course.weekIndexes.first(),
                course.weekIndexes.last()
            )
        courses == (course.weekIndexes.size - 1) * 2 && course.weekIndexes.first() % 2 == 0 ->
            stringResource(
                R.string.week_range_odd,
                course.weekIndexes.first(),
                course.weekIndexes.last()
            )
        else -> course.weekIndexes.toString()
    }
    AhuDialog(onDismissRequest = onDismiss, scrollable = false) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = course.name,
                color = AhuColors.onSurface,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(
                    R.string.course_time_detail,
                    weekPart,
                    numToChinese[course.weekday].orEmpty(),
                    course.startTime,
                    course.startTime + course.length - 1
                ),
                color = AhuColors.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(80.n1 withNight 30.n1)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(24.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = course.location,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(80.n1 withNight 30.n1)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(24.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = course.teacher,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
