package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.AppPageHeader
import kotlinx.coroutines.launch

@Composable
fun XuexiaotongLoginScreen(
    login: suspend (account: String, password: String) -> Unit,
    onBack: (() -> Unit)? = null,
    onLoginSuccess: () -> Unit
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onBack == null) {
                Spacer(modifier = Modifier.height(80.dp))
                Text(
                    text = "学习通日历",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                AppPageHeader(title = "学习通日历", onBack = onBack, horizontalPadding = 0.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "使用学习通账号密码登录",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "登录凭证仅用于登录学习通，不会上传到任何第三方",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(20.dp)
            ) {
                AppTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = "账号（手机号 / 超星号 / 邮箱）",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "密码",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.widthIn(min = 72.dp),
                        variant = AppButtonVariant.Secondary
                    ) {
                        Text(if (showPassword) "隐藏" else "显示", fontSize = 13.sp)
                    }
                }

                if (errorText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                AppButton(
                    onClick = {
                        errorText = ""
                        if (account.isBlank()) {
                            errorText = "请输入账号"
                            return@AppButton
                        }
                        if (password.isBlank()) {
                            errorText = "请输入密码"
                            return@AppButton
                        }
                        loading = true
                        scope.launch {
                            try {
                                login(account, password)
                                onLoginSuccess()
                            } catch (exception: Exception) {
                                errorText = exception.message ?: "登录失败"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !loading
                ) {
                    if (loading) {
                        AppCircularProgressIndicator(
                            size = 20.dp,
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登录中…")
                    } else {
                        Text("登录", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
