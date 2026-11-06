package com.ahu.ahutong

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ahu.ahutong.core.storage.HomeBackgroundStore
import com.ahu.ahutong.feedback.FEEDBACK_ROUTE
import com.ahu.ahutong.feedback.feedbackDestinations
import com.ahu.ahutong.ui.components.AppBackground
import com.ahu.ahutong.ui.components.LiquidGlassAppHost
import com.ahu.ahutong.ui.components.LocalAppBackground
import com.ahu.ahutong.ui.components.LocalAppBackgroundTilt
import com.ahu.ahutong.ui.components.LocalGlassEffectsReduced
import com.ahu.ahutong.ui.components.LocalGlassReadabilityBoost
import com.ahu.ahutong.ui.screen.main.home.rememberBackgroundParallax
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import dagger.hilt.android.AndroidEntryPoint

/** Standalone main-process entry for errors from auxiliary and isolated activities. */
@AndroidEntryPoint
class FeedbackActivity : ComponentActivity() {
    private val preferencesViewModel: PreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val errorDetails = intent.getStringExtra(EXTRA_ERROR_DETAILS)
        val errorEventId = intent.getStringExtra(EXTRA_EVENT_ID)
        setContent {
            val uiTheme by preferencesViewModel.appUiTheme.collectAsState()
            val themeColor by preferencesViewModel.themeColor.collectAsState()
            val themeMode by preferencesViewModel.appThemeMode.collectAsState()
            val hapticFeedbackEnabled by preferencesViewModel.hapticFeedbackEnabled.collectAsState()
            val themeReady by preferencesViewModel.isUiThemePreferenceReady.collectAsState()
            val overrides by preferencesViewModel.componentSlotOverrides.collectAsState()
            val navController = rememberNavController()
            AHUTheme(AhuThemeConfig(
                appUiTheme = uiTheme,
                themeColorHex = themeColor,
                themeMode = themeMode,
                isPreferenceReady = themeReady,
                componentSlotOverrides = overrides,
                hapticFeedbackEnabled = hapticFeedbackEnabled
            )) {
                FeedbackBackgroundHost {
                    NavHost(navController, startDestination = FEEDBACK_ROUTE, modifier = Modifier.fillMaxSize()) {
                        feedbackDestinations(navController, onExit = ::finish,
                            errorDetails = errorDetails, errorEventId = errorEventId)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ERROR_DETAILS = "feedback_error_details"
        const val EXTRA_EVENT_ID = "feedback_event_id"

        fun intent(context: Context, details: String? = null, eventId: String? = null): Intent =
            Intent(context, FeedbackActivity::class.java).apply {
                details?.let { putExtra(EXTRA_ERROR_DETAILS, it) }
                eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
            }
    }
}

/** A separate Activity needs the same wallpaper and backdrop inputs as the main page host. */
@Composable
private fun FeedbackBackgroundHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val backgroundRevision by HomeBackgroundStore.revision.collectAsState()
    val imageRevision by HomeBackgroundStore.imageRevision.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, context, imageRevision) {
        value = if (HomeBackgroundStore.isEnabled) {
            HomeBackgroundStore.loadScreenRatioBitmap(context)?.asImageBitmap()
        } else null
    }
    val background = remember(bitmap, backgroundRevision) {
        bitmap?.let { AppBackground(it, HomeBackgroundStore.maskPercent / 100f) }
    }
    val parallaxEnabled = remember(backgroundRevision) { HomeBackgroundStore.parallaxEnabled }
    val tilt = rememberBackgroundParallax(enabled = background != null && parallaxEnabled)
    val tiltProvider = remember(tilt) { { tilt.value } }
    CompositionLocalProvider(
        LocalAppBackground provides background,
        LocalAppBackgroundTilt provides tiltProvider,
        LocalGlassReadabilityBoost provides (background != null),
        LocalGlassEffectsReduced provides HomeBackgroundStore.reduceGlassEffects
    ) {
        LiquidGlassAppHost(Modifier.fillMaxSize()) { content() }
    }
}
