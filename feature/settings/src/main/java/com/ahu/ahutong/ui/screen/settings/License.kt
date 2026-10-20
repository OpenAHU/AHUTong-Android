package com.ahu.ahutong.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.data.model.License as LicenseItem
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.LicenseViewModel
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun License(
    title: String,
    onBack: () -> Unit,
    licenseViewModel: LicenseViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedLicense by remember { mutableStateOf<LicenseItem?>(null) }

    fun openSource(license: LicenseItem) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(license.url)
            }
        )
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = title,
            onBack = onBack,
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxSize(),
            bottomPadding = 48.dp
        ) {
            SettingsSection(
                title = "开源组件",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                licenseViewModel.license.forEachIndexed { index, license ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .clickable {
                                if (license.licenseAsset != null || license.noticeAsset != null) {
                                    selectedLicense = license
                                } else if (license.name != "Ahu_Plus") {
                                    // Ahu_Plus 仓库按作者策略暂时关闭，不做链接跳转
                                    openSource(license)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = license.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = license.author,
                            color = 30.n1 withNight 90.n1,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = license.url,
                            color = 50.n1 withNight 80.n1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = license.license,
                            color = 50.n1 withNight 80.n1,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (index != licenseViewModel.license.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }

    selectedLicense?.let { license ->
        val licenseText by produceState("", license) {
            value = withContext(Dispatchers.IO) {
                listOfNotNull(license.noticeAsset, license.licenseAsset)
                    .map { assetPath ->
                        runCatching {
                            context.assets.open(assetPath).bufferedReader().use { it.readText() }
                        }.getOrElse {
                            context.getString(R.string.license_load_failed, assetPath)
                        }
                    }
                    .joinToString("\n\n")
            }
        }

        AppDialog(
            title = license.name,
            onDismiss = { selectedLicense = null },
            contentScrollable = true,
            contentSpacing = 12.dp,
            actions = listOf(
                AppDialogAction(
                    stringResource(R.string.close),
                    onClick = { selectedLicense = null }
                ),
                AppDialogAction(
                    stringResource(R.string.view_source),
                    onClick = { openSource(license) },
                    style = AppDialogActionStyle.Primary
                )
            ),
            content = {
                Text(
                    text = license.author,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = license.license,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                SelectionContainer {
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}
