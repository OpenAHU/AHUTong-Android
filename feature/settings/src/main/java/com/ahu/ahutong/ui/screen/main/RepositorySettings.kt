package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.repository.RepositoryAccelerationSource
import com.ahu.ahutong.data.repository.RepositorySourceHealth
import com.ahu.ahutong.data.repository.areRepositoryPrimarySourcesUnavailable
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppPageHeader
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun RepositorySettings(
    navController: NavHostController,
    preferencesViewModel: PreferencesViewModel = hiltViewModel()
) {
    val selectedSourceId by preferencesViewModel.repositoryAccelerationSource.collectAsState()
    val sourceHealth by preferencesViewModel.repositoryAccelerationSourceHealth.collectAsState()
    val isTesting = sourceHealth.values.any { it == RepositorySourceHealth.TESTING }
    val fallbackEnabled = areRepositoryPrimarySourcesUnavailable(
        preferencesViewModel.accelerationSources,
        sourceHealth
    )
    val cardColor = 100.n1 withNight 20.n1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
    ) {
        AppPageHeader(
            title = "学习资料设置",
            onBack = { navController.popBackStack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "GitHub 加速源",
                modifier = Modifier.padding(horizontal = 8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            AppButton(
                onClick = { preferencesViewModel.testRepositoryAccelerationSources(force = true) },
                enabled = !isTesting,
                variant = AppButtonVariant.Secondary
            ) {
                Text(if (isTesting) "正在检测…" else "重新检测")
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appLiquidGlassSurface(
                        shape = SmoothRoundedCornerShape(24.dp),
                        fallbackColor = cardColor,
                        level = LiquidGlassSurfaceLevel.Panel
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                preferencesViewModel.accelerationSources.forEach { source ->
                    RepositoryAccelerationSourceRow(
                        source = source,
                        selected = source.id == selectedSourceId,
                        health = sourceHealth[source.id] ?: RepositorySourceHealth.UNTESTED,
                        enabled = !source.isFallback || fallbackEnabled,
                        onClick = {
                            preferencesViewModel.setRepositoryAccelerationSource(source.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryAccelerationSourceRow(
    source: RepositoryAccelerationSource,
    selected: Boolean,
    health: RepositorySourceHealth,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (selected) 92.a1 withNight 32.a1 else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.name,
                    modifier = Modifier.weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!source.isFallback) {
                    Spacer(modifier = Modifier.width(4.dp))
                    RepositorySourceHealthIndicator(health = health, isDark = isDark)
                }
            }
            Text(
                text = if (source.isFallback) {
                    "仅在其他四个源均不可用时自动启用"
                } else {
                    source.description
                },
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun RepositorySourceHealthIndicator(health: RepositorySourceHealth, isDark: Boolean) {
    val description = when (health) {
        RepositorySourceHealth.UNTESTED -> "尚未检测"
        RepositorySourceHealth.TESTING -> "正在检测"
        RepositorySourceHealth.AVAILABLE -> "可用"
        RepositorySourceHealth.UNAVAILABLE -> "不可用"
    }
    val modifier = Modifier
        .size(18.dp)
        .semantics { stateDescription = description }
    if (health == RepositorySourceHealth.TESTING) {
        AppCircularProgressIndicator(
            modifier = modifier,
            size = 18.dp,
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Icon(
            imageVector = when (health) {
                RepositorySourceHealth.AVAILABLE -> Icons.Rounded.Check
                RepositorySourceHealth.UNAVAILABLE -> Icons.Rounded.Close
                else -> Icons.Rounded.Remove
            },
            contentDescription = description,
            tint = when (health) {
                RepositorySourceHealth.AVAILABLE -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                RepositorySourceHealth.UNAVAILABLE -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            modifier = modifier
        )
    }
}
