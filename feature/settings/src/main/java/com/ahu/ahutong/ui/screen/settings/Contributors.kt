package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.ui.components.AhuListGroup
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.components.AhuSectionTitle
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.DeveloperViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.capsule.ContinuousCapsule

@Composable
fun Contributors(
    developerViewModel: DeveloperViewModel = viewModel()
) {
    val context = LocalContext.current
    AhuScreen {
        AhuPageHeader(
            title = stringResource(id = R.string.contributors),
            titleStyle = MaterialTheme.typography.headlineLarge,
        )
        mapOf(
            developerViewModel.partners to stringResource(id = R.string.mine_tv_partner),
            developerViewModel.developers to stringResource(id = R.string.mine_tv_developer),
        ).forEach { (list, name) ->
            AhuSectionTitle(text = name)
            AhuListGroup {
                list.forEach {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(AhuDimens.ListItemCorner))
                            .background(AhuColors.card)
                            .clickable(role = Role.Button) { it.onclick(context) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (it) {
                            is DeveloperViewModel.Developer -> {
                                AsyncImage(
                                    model = it.img,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(ContinuousCapsule),
                                    contentDescription = null
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = it.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AhuColors.onSurface,
                                    )
                                    Text(
                                        text = it.desc,
                                        color = AhuColors.onSurface.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = stringResource(id = R.string.qq_format, it.qq),
                                        color = AhuColors.onSurface.copy(alpha = 0.55f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            is DeveloperViewModel.Partner -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = it.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AhuColors.onSurface,
                                    )
                                    Text(
                                        text = it.desc,
                                        color = AhuColors.onSurface.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
