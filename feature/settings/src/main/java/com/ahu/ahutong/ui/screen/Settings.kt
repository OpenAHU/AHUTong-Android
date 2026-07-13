package com.ahu.ahutong.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuInsetCard
import com.ahu.ahutong.ui.components.AhuListGroup
import com.ahu.ahutong.ui.components.AhuListItem
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.components.AhuSectionTitle
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.state.AboutViewModel
import com.kyant.capsule.ContinuousCapsule

/**
 * Settings shell. App-side effects (update check / clear session) are injected by the host
 * so this feature does not depend on MainViewModel, crawler, or AHUCache.
 */
@SuppressLint("ContextCastToActivity")
@Composable
fun Settings(
    navController: NavHostController,
    aboutViewModel: AboutViewModel = hiltViewModel(),
    userName: String? = null,
    schoolTerm: String? = null,
    onCheckUpdate: (onResult: (String) -> Unit) -> Unit = {},
    onClearAllData: () -> Unit = {},
    loadUpdateLog: suspend () -> String = { "暂无更新说明" },
) {
    val context = LocalContext.current as ComponentActivity
    var isClearCacheDialogShown by rememberSaveable { mutableStateOf(false) }
    var isUpdateLogDialogShown by rememberSaveable { mutableStateOf(false) }
    val tip by remember { aboutViewModel.tipState }
    var updateLog by remember { mutableStateOf("") }

    val appIconBitmap = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            drawable.toBitmap()
        }
        bitmap.asImageBitmap()
    }

    LaunchedEffect(tip) {
        tip?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            aboutViewModel.tipState.value = null
        }

        updateLog = runCatching { loadUpdateLog() }
            .getOrElse { "获取失败" }
    }

    AhuScreen(clearBottomNav = true) {
        AhuPageHeader(
            title = stringResource(id = R.string.setting),
            titleStyle = MaterialTheme.typography.headlineLarge,
        )
        var count by remember { mutableStateOf(0) }
        var lastClickTime by remember { mutableStateOf(0L) }
        val clickTimes: Int = 8
        val interval: Long = 1000
        val checkUpdate = {
            onCheckUpdate { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        val onAppCardClick = {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > interval) {
                count = 1
            } else {
                count++
            }
            lastClickTime = now

            if (count >= clickTimes) {
                count = 0
                navController.navigate("debug")
            }
        }

        AhuInsetCard(
            containerColor = AhuColors.accentSurface,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(onAppCardClick) {
                    detectTapGestures { onAppCardClick() }
                }
            ) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(ContinuousCapsule)
                        .background(AhuColors.card)
                        .padding(4.dp)
                        .size(64.dp)
                        .clip(ContinuousCapsule)
                        .scale(1.75f)
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = aboutViewModel.versionName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        AhuSectionTitle(text = "账户信息")
        userName?.let { name ->
            AhuInsetCard(
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    schoolTerm?.let { term ->
                        val data = term.split('-')  //2025-2026-1
                        if (data.size == 3) {
                            Text(
                                text = "第${data[0]}-${data[1]}学年 第${data[2]}学期",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ContinuousCapsule)
                            .background(AhuColors.cardStrong)
                            .clickable { navController.navigate("login") }
                            .padding(12.dp, 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "重新登录",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        AhuListGroup {
            AhuListItem(
                label = stringResource(id = R.string.preferences),
                icon = Icons.Outlined.Tune,
                onClick = { navController.navigate("preferences") },
            )
        }

        AhuSectionTitle(text = "关于")
        AhuListGroup {
            AhuListItem(
                label = stringResource(id = R.string.license),
                icon = Icons.Outlined.Article,
                onClick = { navController.navigate("settings__license") },
            )
            AhuListItem(
                label = stringResource(id = R.string.contributors),
                icon = Icons.Outlined.PeopleOutline,
                onClick = { navController.navigate("settings__contributors") },
            )
            AhuListItem(
                label = stringResource(id = R.string.mine_tv_feedback),
                icon = Icons.Outlined.Feedback,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1006203134&card_type=group&source=qrcode".toUri()
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                    } catch (e: Exception) {
                        Toast
                            .makeText(context, "请安装 QQ 或 Tim", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
            )
            AhuListItem(
                label = stringResource(id = R.string.setting_clear),
                icon = Icons.Outlined.ClearAll,
                onClick = { isClearCacheDialogShown = true },
            )
            AhuListItem(
                label = stringResource(id = R.string.check_update),
                icon = Icons.Outlined.Update,
                onClick = { checkUpdate() },
            )
            AhuListItem(
                label = stringResource(id = R.string.update_intro),
                icon = Icons.Outlined.Article,
                onClick = { isUpdateLogDialogShown = true },
            )
        }
    }
    if (isClearCacheDialogShown) {
        AhuDialog(onDismissRequest = { isClearCacheDialogShown = false }) {
            Text(
                text = "您的登录状态、课表等信息将会被永久清除",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge
            )
            AhuPrimaryButton(
                text = "清除",
                onClick = {
                    onClearAllData()
                    Toast
                        .makeText(context, "已清除所有数据", Toast.LENGTH_SHORT)
                        .show()
                    navController.navigate("login") {
                        popUpTo(0)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    if (isUpdateLogDialogShown) {
        AhuDialog(onDismissRequest = { isUpdateLogDialogShown = false }) {
            Text(
                text = stringResource(id = R.string.update_intro),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = updateLog,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
