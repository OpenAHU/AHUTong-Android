package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle

@Composable
internal fun PostgraduateWeekDialog(
    termCode: String,
    termName: String,
    currentWeek: Int?,
    required: Boolean,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable(termCode) { mutableStateOf(currentWeek?.toString().orEmpty()) }
    val week = input.trim().toIntOrNull()
    val valid = week != null && week in 1..PostgraduateTeachingWeek.MAX_WEEK
    AppDialog(
        onDismiss = { if (!required) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required),
        title = "设置研究生当前周",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(termName)
                Text("请输入本周是第几教学周。保存后将按日期自动更新，也可随时在课表设置中修改。")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(8) },
                    label = { Text("当前教学周") },
                    supportingText = { Text("请输入 1–${PostgraduateTeachingWeek.MAX_WEEK} 的整数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = input.isNotBlank() && !valid
                )
            }
        },
        actions = buildList {
            if (!required) add(AppDialogAction("取消", onClick = onDismiss))
            add(AppDialogAction("保存", onClick = { week?.let(onSave) },
                enabled = valid, style = AppDialogActionStyle.Primary))
        }
    )
}
