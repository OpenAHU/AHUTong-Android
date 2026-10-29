package com.ahu.ahutong.ui.component

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import java.util.WeakHashMap
import kotlinx.coroutines.delay

@Composable
fun SecurePaymentPasswordDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    errorMessage: String? = null,
    useBuiltInKeyboard: Boolean? = null
) {
    SecureWindowEffect()

    when (useBuiltInKeyboard) {
        true -> BuiltInSecurePaymentPasswordDialog(
            password = password,
            onPasswordChange = onPasswordChange,
            title = title,
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
            errorMessage = errorMessage
        )

        false -> SystemPaymentPasswordDialog(
            password = password,
            onPasswordChange = onPasswordChange,
            title = title,
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
            errorMessage = errorMessage
        )

        null -> Unit
    }
}

@Composable
private fun BuiltInSecurePaymentPasswordDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    errorMessage: String?
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        PasswordDots(passwordLength = password.length)
                        errorMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismissRequest) {
                                Text("取消")
                            }
                            TextButton(
                                onClick = { onConfirm(password) },
                                enabled = password.length == PASSWORD_LENGTH
                            ) {
                                Text("确认")
                            }
                        }
                    }
                }
            }

            SecureWindowEffect()
            NumericPasswordKeypad(
                onDigit = { digit ->
                    if (password.length < PASSWORD_LENGTH) {
                        onPasswordChange(password + digit)
                    }
                },
                onBackspace = {
                    if (password.isNotEmpty()) onPasswordChange(password.dropLast(1))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SystemPaymentPasswordDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    errorMessage: String?
) {
    val focusRequester = remember { FocusRequester() }

    AppDialog(
        onDismiss = onDismissRequest,
        title = title,
        content = {
            SecureWindowEffect()
            val keyboardController = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                delay(SYSTEM_KEYBOARD_FOCUS_DELAY_MS)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { value ->
                        if (value.length <= PASSWORD_LENGTH && value.all(Char::isDigit)) {
                            onPasswordChange(value)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("6 位数字密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.length == PASSWORD_LENGTH) onConfirm(password)
                        }
                    ),
                    isError = errorMessage != null,
                    singleLine = true
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        actions = listOf(
            AppDialogAction("取消", onClick = onDismissRequest),
            AppDialogAction("确认", onClick = { onConfirm(password) },
                enabled = password.length == PASSWORD_LENGTH, style = AppDialogActionStyle.Primary)
        )
    )
}

@Composable
private fun PasswordDots(passwordLength: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "已输入 $passwordLength 位，共 $PASSWORD_LENGTH 位"
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(PASSWORD_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .then(
                        if (index < passwordLength) {
                            Modifier.background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun NumericPasswordKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("123", "456", "789").forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowDigits.forEach { digit ->
                    PasswordKey(
                        label = digit.toString(),
                        contentDescription = "数字 $digit",
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(KEY_HEIGHT)
            )
            PasswordKey(
                label = "0",
                contentDescription = "数字 0",
                onClick = { onDigit('0') },
                modifier = Modifier.weight(1f)
            )
            PasswordKey(
                label = "⌫",
                contentDescription = "删除上一位",
                onClick = onBackspace,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PasswordKey(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .height(KEY_HEIGHT)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SecureWindowEffect() {
    val activityWindow = LocalActivity.current?.window
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    val windows = listOfNotNull(activityWindow, dialogWindow).distinct()
    DisposableEffect(windows) {
        windows.forEach(SecureWindowRegistry::acquire)
        onDispose {
            windows.forEach(SecureWindowRegistry::release)
        }
    }
}

private object SecureWindowRegistry {
    private data class WindowState(
        var holderCount: Int,
        val wasSecureBeforeAcquire: Boolean
    )

    private val states = WeakHashMap<Window, WindowState>()

    @Synchronized
    fun acquire(window: Window) {
        val existing = states[window]
        if (existing != null) {
            existing.holderCount += 1
            return
        }

        val wasSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (!wasSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        states[window] = WindowState(
            holderCount = 1,
            wasSecureBeforeAcquire = wasSecure
        )
    }

    @Synchronized
    fun release(window: Window) {
        val state = states[window] ?: return
        state.holderCount -= 1
        if (state.holderCount <= 0) {
            states.remove(window)
            if (!state.wasSecureBeforeAcquire) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private const val PASSWORD_LENGTH = 6
private const val SYSTEM_KEYBOARD_FOCUS_DELAY_MS = 200L
private val KEY_HEIGHT = 56.dp
