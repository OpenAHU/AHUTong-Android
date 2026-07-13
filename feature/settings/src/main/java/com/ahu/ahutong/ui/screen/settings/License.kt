package com.ahu.ahutong.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.ui.components.AhuListGroup
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.LicenseViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

@Composable
fun License(
    licenseViewModel: LicenseViewModel = viewModel()
) {
    val context = LocalContext.current
    AhuScreen {
        AhuPageHeader(
            title = stringResource(id = R.string.license),
            titleStyle = MaterialTheme.typography.headlineLarge,
        )
        AhuListGroup {
            licenseViewModel.license.forEach {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SmoothRoundedCornerShape(AhuDimens.ListItemCorner))
                        .background(AhuColors.card)
                        .clickable(role = Role.Button) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(it.url)
                                }
                            )
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = it.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = AhuColors.onSurface,
                    )
                    Text(
                        text = it.author,
                        color = AhuColors.onSurface.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = it.url,
                        color = AhuColors.onSurface.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
