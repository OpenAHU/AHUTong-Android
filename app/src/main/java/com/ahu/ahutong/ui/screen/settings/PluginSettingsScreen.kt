package com.ahu.ahutong.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.plugin.PluginManagerSection

private const val PLUGIN_TEMPLATE_URL = "https://github.com/inchange-jiang/ahutong-plugin"

@Composable
fun PluginSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "插件装载",
            onBack = onBack,
            backdrop = backdrop
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PluginManagerSection(backdrop = backdrop)
                Text(
                    text = "想自己开发插件？",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 24.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLUGIN_TEMPLATE_URL)))
                            }.onFailure {
                                Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
