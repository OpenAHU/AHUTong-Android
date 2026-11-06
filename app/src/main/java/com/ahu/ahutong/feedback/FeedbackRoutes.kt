package com.ahu.ahutong.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.ahu.ahutong.ui.screen.settings.FeedbackExportScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackHubScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackScreen
import com.ahu.ahutong.utils.animatedComposable

internal const val FEEDBACK_ROUTE = "settings__feedback"
internal const val FEEDBACK_HUB_ROUTE = "settings__feedback_hub"
internal const val FEEDBACK_FORM_ROUTE = "settings__feedback_form"
internal const val FEEDBACK_EXPORT_ROUTE = "settings__feedback_export"

/** Feedback follows the host's normal back stack, page lifecycle and secondary-page transitions. */
internal fun NavGraphBuilder.feedbackDestinations(
    navController: NavHostController,
    onExit: () -> Unit,
    errorDetails: String? = null,
    errorEventId: String? = null
) {
    navigation(
        route = FEEDBACK_ROUTE,
        startDestination = if (errorDetails.isNullOrBlank()) FEEDBACK_HUB_ROUTE else FEEDBACK_FORM_ROUTE
    ) {
        animatedComposable(FEEDBACK_HUB_ROUTE) { entry ->
            val viewModel = feedbackViewModel(navController, entry, errorDetails, errorEventId)
            val context = LocalContext.current
            BackHandler(onBack = onExit)
            FeedbackHubScreen(
                onFeedback = { navController.navigate(FEEDBACK_FORM_ROUTE) },
                onExport = {
                    viewModel.dismissPreview()
                    viewModel.reportExportStatus("")
                    navController.navigate(FEEDBACK_EXPORT_ROUTE)
                },
                onOpenQq = { openFeedbackGroup(context) },
                onBack = onExit
            )
        }
        animatedComposable(FEEDBACK_FORM_ROUTE) { entry ->
            val viewModel = feedbackViewModel(navController, entry, errorDetails, errorEventId)
            val state by viewModel.state.collectAsState()
            val onBack = {
                viewModel.dismissPreview()
                backToFeedbackHub(navController)
            }
            BackHandler(onBack = onBack)
            FeedbackScreen(
                state = state,
                onDescriptionChange = viewModel::setDescription,
                onCategoryChange = viewModel::setCategory,
                onPreview = viewModel::showPreview,
                onDismissPreview = viewModel::dismissPreview,
                onSubmit = viewModel::submit,
                onBack = onBack
            )
        }
        animatedComposable(FEEDBACK_EXPORT_ROUTE) { entry ->
            val viewModel = feedbackViewModel(navController, entry, errorDetails, errorEventId)
            val state by viewModel.state.collectAsState()
            val (actions, exportLogs) = rememberFeedbackLogActions(viewModel)
            val onBack = { backToFeedbackHub(navController) }
            BackHandler(onBack = onBack)
            FeedbackExportScreen(
                state = state,
                onCategoryChange = viewModel::setCategory,
                onExport = exportLogs,
                onShare = actions::shareLogs,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun feedbackViewModel(
    navController: NavHostController,
    entry: NavBackStackEntry,
    errorDetails: String?,
    errorEventId: String?
): FeedbackViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(FEEDBACK_ROUTE) }
    val model: FeedbackViewModel = viewModel(parent)
    LaunchedEffect(model) { model.attachError(errorDetails, errorEventId) }
    return model
}

private fun backToFeedbackHub(navController: NavHostController) {
    if (!navController.popBackStack(FEEDBACK_HUB_ROUTE, inclusive = false)) {
        // Error entries open the form directly and have no hub entry to return to yet.
        navController.navigate(FEEDBACK_HUB_ROUTE) {
            popUpTo(FEEDBACK_ROUTE)
            launchSingleTop = true
        }
    }
}
