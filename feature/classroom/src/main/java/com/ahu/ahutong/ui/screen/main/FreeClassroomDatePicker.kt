package com.ahu.ahutong.ui.screen.main

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.ahu.ahutong.ui.theme.AhuColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDatePickerDialog(
    initialDate: LocalDate,
    minDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= minDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        }
    )

    val colors = DatePickerDefaults.colors(
        containerColor = AhuColors.card,
        titleContentColor = AhuColors.onSurface,
        headlineContentColor = AhuColors.onSurface,
        weekdayContentColor = AhuColors.onSurface.copy(alpha = 0.55f),
        subheadContentColor = AhuColors.onSurface.copy(alpha = 0.55f),
        yearContentColor = AhuColors.onSurface.copy(alpha = 0.55f),
        currentYearContentColor = AhuColors.onSurface,
        selectedYearContentColor = AhuColors.onPrimaryAction,
        selectedYearContainerColor = AhuColors.primaryAction,
        dayContentColor = AhuColors.onSurface,
        disabledDayContentColor = AhuColors.onSurface.copy(alpha = 0.35f),
        selectedDayContentColor = AhuColors.onPrimaryAction,
        disabledSelectedDayContentColor = AhuColors.onSurface.copy(alpha = 0.35f),
        selectedDayContainerColor = AhuColors.primaryAction,
        disabledSelectedDayContainerColor = AhuColors.primaryAction,
        todayContentColor = AhuColors.onSurface,
        todayDateBorderColor = AhuColors.primaryAction,
        navigationContentColor = AhuColors.onSurface
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AhuColors.onSurface
                )
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AhuColors.onSurface
                )
            ) {
                Text("取消")
            }
        },
        colors = colors
    ) {
        DatePicker(
            state = datePickerState,
            colors = colors
        )
    }
}
