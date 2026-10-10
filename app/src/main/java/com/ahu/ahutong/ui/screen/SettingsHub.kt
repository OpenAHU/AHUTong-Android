package com.ahu.ahutong.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.session.SavedAccounts
import com.ahu.ahutong.ui.screen.setup.SavedAccountsDialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.R
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.ui.state.MainViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel

/**
 * 设置枢纽页的宿主外壳：把"只有宿主才知道"的东西传进去——导航目标、更新检查动作、
 * 账户名与学年学期摘要、以及这是不是调试构建。
 *
 * 页面本身（:feature:settings）因此只认识回调与要显示的值，不认识路由字符串，
 * 也不认识主界面的更新对话框状态机。
 */
@Composable
fun SettingsHub(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    scheduleViewModel: ScheduleViewModel
) {
    val context = LocalContext.current
    var showAccounts by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(SavedAccounts.accounts()) }
    fun openAccounts() {
        runCatching {
            SavedAccounts.rememberCurrent()
            accounts = SavedAccounts.accounts()
            showAccounts = true
        }.onFailure {
            Toast.makeText(context, "无法安全读取账号，请重试", Toast.LENGTH_SHORT).show()
        }
    }
    Settings(
        onNavigateToLogin = { navController.navigate("login") },
        onSwitchAccount = { openAccounts() },
        onNavigateToPreferences = { navController.navigate("preferences") },
        onNavigateToDebug = { navController.navigate("debug") },
        onNavigateToLicense = { navController.navigate("settings__license") },
        onNavigateToPrivacyPolicy = { navController.navigate("settings__privacy_policy") },
        onNavigateToContributors = { navController.navigate("settings__contributors") },
        onDataCleared = { navController.navigate("login") { popUpTo(0) } },
        onCheckUpdate = mainViewModel::checkApkUpdateManually,
        accountName = SessionStore.currentUser()?.name,
        scheduleSummary = if (AHUCache.canUseUndergraduateAcademics()) {
            "${scheduleViewModel.schoolYear} 学年 · 第 ${scheduleViewModel.schoolTerm} 学期"
        } else {
            "研究生账号"
        },
        debugBuild = BuildConfig.DEBUG,
        appName = stringResource(R.string.app_name),
        appIcon = painterResource(R.mipmap.ic_launcher_foreground),
        licenseTitle = stringResource(R.string.license),
        contributorsTitle = stringResource(R.string.contributors)
    )
    if (showAccounts) {
        SavedAccountsDialog(
            accounts = accounts,
            currentUserId = SessionStore.currentUser()?.xh,
            onSelect = { userId ->
                showAccounts = false
                (context as? MainActivity)?.openAccountLogin(userId)
            },
            onForget = { userId ->
                runCatching {
                    SavedAccounts.forget(userId)
                    accounts = SavedAccounts.accounts()
                }.onFailure {
                    Toast.makeText(context, "移除账号失败，请重试", Toast.LENGTH_SHORT).show()
                }
            },
            onAdd = {
                showAccounts = false
                (context as? MainActivity)?.openAccountLogin(null)
            },
            onDismiss = { showAccounts = false }
        )
    }
}
