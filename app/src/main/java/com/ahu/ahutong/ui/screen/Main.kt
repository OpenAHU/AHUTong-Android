package com.ahu.ahutong.ui.screen

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import android.webkit.CookieManager as WebkitCookieManager
import com.ahu.ahutong.appwidget.ScheduleAppWidgetReceiver
import com.ahu.ahutong.core.common.AppSessionState
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.gray.GrayFeatures
import com.ahu.ahutong.data.gray.GrayReleaseManager
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.sdk.RustSDK
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.screen.main.BathroomDeposit
import com.ahu.ahutong.ui.screen.main.CardBalanceDeposit
import com.ahu.ahutong.ui.screen.main.ElectricityDeposit
import com.ahu.ahutong.ui.screen.main.Exam
import com.ahu.ahutong.ui.screen.main.FreeClassroom
import com.ahu.ahutong.ui.screen.main.Grade
import com.ahu.ahutong.ui.screen.main.Home
import com.ahu.ahutong.ui.screen.main.LostFound
import com.ahu.ahutong.ui.screen.main.PhoneBook
import com.ahu.ahutong.ui.screen.main.Schedule
import com.ahu.ahutong.ui.screen.main.SchoolCalendar
import com.ahu.ahutong.ui.screen.main.Tools
import com.ahu.ahutong.ui.screen.main.Repository
import com.ahu.ahutong.ui.screen.main.RepositoryDownloads
import com.ahu.ahutong.ui.screen.main.Weather
import com.ahu.ahutong.ui.screen.settings.Contributors
import com.ahu.ahutong.ui.screen.settings.Debug
import com.ahu.ahutong.ui.screen.settings.License
import com.ahu.ahutong.ui.screen.settings.Preferences
import com.ahu.ahutong.ui.screen.setup.Info
import com.ahu.ahutong.ui.screen.setup.Login
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.ApkUpdateViewModel
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.update.loadApkUpdateChangelog
import com.ahu.ahutong.utils.animatedComposable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Main(
    navController: NavHostController,
    apkUpdateViewModel: ApkUpdateViewModel = viewModel(),
    loginViewModel: LoginViewModel = hiltViewModel(),
    discoveryViewModel: DiscoveryViewModel = hiltViewModel(),
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    aboutViewModel: AboutViewModel = hiltViewModel(),
    isReLoginShown: Boolean,
    onReLoginDismiss: () -> Unit
) {
    var shouldEnterHomeEdit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()
    var homeEditGrayState by remember {
        mutableStateOf(GrayReleaseManager.localState(GrayFeatures.HomeEdit, context))
    }

    LaunchedEffect(Unit) {
        homeEditGrayState = GrayReleaseManager.state(GrayFeatures.HomeEdit, context)
    }

    Box {
        val backdrop = rememberLayerBackdrop()
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize()
                .background(AhuColors.pageBackground)
        ) {
            animatedComposable("home") {
                Home(
                    discoveryViewModel = discoveryViewModel,
                    scheduleViewModel = scheduleViewModel,
                    navController = navController,
                    homeEditEnabled = homeEditGrayState.enabled,
                    enterEditModeRequest = shouldEnterHomeEdit,
                    onEnterEditModeRequestConsumed = {
                        shouldEnterHomeEdit = false
                    },
                    mockRefreshRevision = mockRefreshRevision,
                )
            }
            animatedComposable("setup") {
                Setup(
                    scheduleViewModel = scheduleViewModel,
                    versionName = aboutViewModel.versionName,
                    isLoggedIn = AHUCache.isLogin(),
                    onLegacyCacheClear = { AHUCache.clearAll() },
                    onSetup = {
                        navController.popBackStack()
                        discoveryViewModel.loadActivityBean()
                        scheduleViewModel.loadConfig()
                        scheduleViewModel.refreshSchedule()
                        scope.launch {
                            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                ScheduleAppWidgetReceiver::class.java
                            )
                        }
                    }
                )
            }
            animatedComposable("login") {
                Login(
                    loginViewModel = loginViewModel,
                    onLoggedIn = {
                        scheduleViewModel.clear()
                        scope.launch {
                            homeEditGrayState = GrayReleaseManager.state(
                                GrayFeatures.HomeEdit,
                                context
                            )
                        }
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                        discoveryViewModel.loadActivityBean()
                        scheduleViewModel.loadConfig()
                        scheduleViewModel.refreshSchedule()
                    }
                )
            }
            animatedComposable("info") {
                Info(
                    scheduleViewModel = scheduleViewModel,
                    onSetup = { navController.popBackStack() }
                )
            }
            animatedComposable("schedule") {
                Schedule(scheduleViewModel = scheduleViewModel)
            }
            animatedComposable("tools") {
                Tools(
                    navController = navController,
                    homeEditEnabled = homeEditGrayState.enabled,
                    onEditHome = {
                        shouldEnterHomeEdit = true
                    },
                    placedWidgetIds = AHUCache.getHomeWidgetSlots().filterNotNull().toSet(),
                    onPinScheduleWidget = {
                        scope.launch {
                            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                ScheduleAppWidgetReceiver::class.java
                            )
                        }
                    },
                )
            }
            animatedComposable("school_calendar") {
                SchoolCalendar(
                    navController = navController,
                    mockRefreshRevision = mockRefreshRevision,
                    isMockMode = AHUCache.getMockData(),
                )
            }
            animatedComposable("grade") {
                Grade(mockRefreshRevision = mockRefreshRevision)
            }
            animatedComposable("phone_book") {
                PhoneBook()
            }
            animatedComposable("exam") {
                Exam(mockRefreshRevision = mockRefreshRevision)
            }
            animatedComposable("free_classroom") {
                FreeClassroom(mockRefreshRevision = mockRefreshRevision)
            }
            animatedComposable("lost_found") {
                LostFound(mockRefreshRevision = mockRefreshRevision)
            }
            animatedComposable("weather") {
                Weather()
            }
            animatedComposable("repository") {
                Repository(navController = navController)
            }
            animatedComposable("repository_downloads") {
                RepositoryDownloads(navController = navController)
            }
            animatedComposable("settings") {
                Settings(
                    navController = navController,
                    aboutViewModel = aboutViewModel,
                    userName = AHUCache.getCurrentUser()?.name,
                    schoolTerm = AHUCache.getSchoolTerm(),
                    onCheckUpdate = { onResult ->
                        apkUpdateViewModel.checkApkUpdateManually(context, onResult)
                    },
                    onClearAllData = {
                        AHUCache.logout()
                        WebkitCookieManager.getInstance().removeAllCookies(null)
                        WebkitCookieManager.getInstance().flush()
                        AHUCache.clearAll()
                        RustSDK.init("")
                        CookieManager.cookieJar.clear()
                        CookieManager.cookieJar.clearSession()
                        AppSessionState.sessionExpired = true
                    },
                    loadUpdateLog = { loadApkUpdateChangelog() },
                )
            }
            animatedComposable("settings__license") {
                License()
            }
            animatedComposable("settings__contributors") {
                Contributors()
            }

            animatedComposable("preferences") {
                Preferences()
            }

            animatedComposable("electricity_pay") {
                ElectricityDeposit()
            }

            animatedComposable("card_balance_deposit") {
                CardBalanceDeposit(mockRefreshRevision = mockRefreshRevision)
            }

            animatedComposable("bathroom_deposit") {
                BathroomDeposit()
            }

            animatedComposable("debug") {
                Debug(
                    scheduleViewModel = scheduleViewModel,
                    discoveryViewModel = discoveryViewModel,
                    onGrayStateChanged = {
                        scope.launch {
                            homeEditGrayState = GrayReleaseManager.state(
                                GrayFeatures.HomeEdit,
                                context
                            )
                        }
                    }
                )
            }

            animatedComposable("splash") {
                Splash(
                    navController = navController,
                    isAgreementAccepted = AHUCache.isAgreementAccepted(),
                    isPrivacyAccepted = AHUCache.isPrivacyAccepted(),
                    isBusinessAccepted = AHUCache.isBusinessAccepted(),
                    isLoggedIn = AHUCache.isLogin() || AHUCache.getMockData(),
                    onAcceptAgreement = { AHUCache.setAgreementAccepted() },
                    onAcceptPrivacy = { AHUCache.setPrivacyAccepted() },
                    onAcceptBusiness = { AHUCache.setBusinessAccepted() },
                )
            }


        }
        BottomNavBar(navController, backdrop)
    }
    if (isReLoginShown) {
        AhuDialog(
            onDismissRequest = { onReLoginDismiss() },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Text(
                text = "当前登录状态已过期，请重新登录!",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge
            )
            AhuPrimaryButton(
                text = "重新登录",
                onClick = {
                    navController.navigate("login")
                    onReLoginDismiss()
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
