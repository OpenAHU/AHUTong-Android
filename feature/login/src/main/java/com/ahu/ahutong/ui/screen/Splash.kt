package com.ahu.ahutong.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ahu.ahutong.feature.login.R
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * First-launch agreement gate. Consent flags and login state are provided by the app host
 * (typically AHUCache) so this feature stays free of app-only storage.
 */
@Composable
fun Splash(
    navController: NavController,
    isAgreementAccepted: Boolean,
    isPrivacyAccepted: Boolean,
    isBusinessAccepted: Boolean,
    isLoggedIn: Boolean,
    onAcceptAgreement: () -> Unit,
    onAcceptPrivacy: () -> Unit,
    onAcceptBusiness: () -> Unit,
) {
    var showAgreementDialog by remember { mutableStateOf(!isAgreementAccepted) }
    var showPrivacyDialog by remember { mutableStateOf(!isPrivacyAccepted) }
    var showBusinessDialog by remember { mutableStateOf(!isBusinessAccepted) }
    var agreementAccepted by remember { mutableStateOf(isAgreementAccepted) }

    val activity = LocalActivity.current

    LaunchedEffect(agreementAccepted) {
        if (agreementAccepted) {
            if (isLoggedIn) {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    if (showAgreementDialog) {
        AgreementDialog(
            onAgree = {
                onAcceptAgreement()
                agreementAccepted = true
                showAgreementDialog = false
            },
            onDisagree = {
                activity?.finish()
            }
        )
    }
    if (showPrivacyDialog) {
        PrivacyDialog(
            onAgree = {
                onAcceptPrivacy()
                showPrivacyDialog = false
            },
            onDisagree = {
                activity?.finish()
            }
        )
    }
    if (showBusinessDialog) {
        BusinessDialog(
            onAgree = {
                onAcceptBusiness()
                showBusinessDialog = false
            },
            onDisagree = {
                activity?.finish()
            }
        )
    }
}

@Composable
fun AgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    ConsentAlertDialog(
        title = stringResource(R.string.agreement_title),
        body = stringResource(R.string.agreement_body),
        onAgree = onAgree,
        onDisagree = onDisagree,
    )
}

@Composable
fun PrivacyDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    ConsentAlertDialog(
        title = stringResource(R.string.privacy_title),
        body = stringResource(R.string.privacy_body),
        onAgree = onAgree,
        onDisagree = onDisagree,
    )
}

@Composable
fun BusinessDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    ConsentAlertDialog(
        title = stringResource(R.string.business_title),
        body = stringResource(R.string.business_body),
        onAgree = onAgree,
        onDisagree = onDisagree,
    )
}

@Composable
private fun ConsentAlertDialog(
    title: String,
    body: String,
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = AhuColors.onSurface
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AhuColors.onSurface
                )
            }
        },
        shape = SmoothRoundedCornerShape(AhuDimens.CardCorner),
        confirmButton = {
            AhuPrimaryButton(
                text = stringResource(R.string.agree),
                onClick = onAgree,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            )
        },
        dismissButton = {
            AhuPrimaryButton(
                text = stringResource(R.string.disagree),
                onClick = onDisagree,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier.padding(end = 4.dp),
            )
        },
        containerColor = AhuColors.card
    )
}
