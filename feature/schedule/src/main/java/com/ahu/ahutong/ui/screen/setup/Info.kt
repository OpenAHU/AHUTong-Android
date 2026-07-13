package com.ahu.ahutong.ui.screen.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.feature.schedule.R
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreenBox
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.capsule.ContinuousCapsule

@Composable
fun Info(
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    onSetup: () -> Unit
) {
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()
    var schoolYear by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(scheduleViewModel.schoolYear))
    }
    var schoolTerm by rememberSaveable { mutableStateOf(scheduleViewModel.schoolTerm) }
    var currentWeek by rememberSaveable(scheduleConfig?.week, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(scheduleConfig?.week?.toString() ?: "1"))
    }
    AhuScreenBox(
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(AhuDimens.ContentHorizontal)
        ) {
            AhuPageHeader(title = stringResource(id = R.string.fill_in_info))
            Spacer(modifier = Modifier)
            Text(
                text = stringResource(id = R.string.school_year),
                modifier = Modifier.padding(horizontal = AhuDimens.TitleHorizontal),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            BasicTextField(
                value = schoolYear,
                onValueChange = { schoolYear = it },
                modifier = Modifier
                    .padding(horizontal = AhuDimens.ContentHorizontal)
                    .clip(ContinuousCapsule)
                    .background(AhuColors.card),
                textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                cursorBrush = SolidColor(LocalContentColor.current)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AhuDimens.TitleHorizontal)
                        .height(64.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    it()
                }
            }
            Text(
                text = stringResource(id = R.string.school_term),
                modifier = Modifier.padding(horizontal = AhuDimens.TitleHorizontal),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(
                modifier = Modifier
                    .padding(horizontal = AhuDimens.ContentHorizontal)
                    .clip(ContinuousCapsule)
                    .background(AhuColors.card),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(arrayOf("1", "2", "3")) {
                    val isSelected = it == schoolTerm
                    Text(
                        text = it,
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(if (isSelected) AhuColors.chipSelected else Color.Unspecified)
                            .clickable { schoolTerm = it }
                            .padding(16.dp, 8.dp),
                        color = if (isSelected) AhuColors.onPrimaryAction else Color.Unspecified,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Text(
                text = stringResource(id = R.string.current_week),
                modifier = Modifier.padding(horizontal = AhuDimens.TitleHorizontal),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            BasicTextField(
                value = currentWeek,
                onValueChange = { currentWeek = it },
                modifier = Modifier
                    .padding(horizontal = AhuDimens.ContentHorizontal)
                    .clip(ContinuousCapsule)
                    .background(AhuColors.card),
                textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    scheduleViewModel.saveTime(
                        schoolYear = schoolYear.text,
                        schoolTerm = schoolTerm,
                        week = currentWeek.text.toIntOrNull() ?: 1
                    )
                    onSetup()
                }),
                singleLine = true,
                cursorBrush = SolidColor(LocalContentColor.current)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AhuDimens.TitleHorizontal)
                        .height(64.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    it()
                }
            }
        }
        AhuPrimaryButton(
            text = stringResource(id = R.string.ok),
            onClick = {
                scheduleViewModel.saveTime(
                    schoolYear = schoolYear.text,
                    schoolTerm = schoolTerm,
                    week = currentWeek.text.toIntOrNull() ?: 1
                )
                onSetup()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(AhuDimens.ContentHorizontal),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}
