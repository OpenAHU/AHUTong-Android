package com.ahu.ahutong.ui.screen.settings

import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.state.DeveloperViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun Contributors(
    title: String,
    onBack: () -> Unit,
    developerViewModel: DeveloperViewModel = viewModel()
) {
    val context = LocalContext.current
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = title,
            onBack = onBack,
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxSize(),
            bottomPadding = 48.dp
        ) {
            mapOf(
                developerViewModel.partners to stringResource(id = R.string.mine_tv_partner),
                developerViewModel.developers to stringResource(id = R.string.mine_tv_developer),
            ).forEach { (list, name) ->
                SettingsSection(
                    title = name,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    backdrop = backdrop
                ) {
                    list.forEachIndexed { index, contributor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .clickable { contributor.onclick(context) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (contributor) {
                                is DeveloperViewModel.Developer -> {
                                    AsyncImage(
                                        model = contributor.img,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(ContinuousCapsule),
                                        contentDescription = null
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = contributor.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = contributor.desc,
                                            color = 30.n1 withNight 90.n1,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "QQ: ${contributor.qq}",
                                            color = 50.n1 withNight 80.n1,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                is DeveloperViewModel.Partner -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = contributor.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = contributor.desc,
                                            color = 30.n1 withNight 90.n1,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                        if (index != list.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}
