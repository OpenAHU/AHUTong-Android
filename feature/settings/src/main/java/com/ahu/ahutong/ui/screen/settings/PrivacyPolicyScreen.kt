package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.markdown.AppMarkdown

/**
 * 隐私政策全文阅读页。内容由 :app 从 res/raw 注入（资源归属约定同 License/Contributors）。
 */
@Composable
fun PrivacyPolicyScreen(markdown: String, onBack: () -> Unit) {
    AppPageScaffold(
        title = "隐私政策",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        freeContent = {
            AppMarkdown(
                markdown = markdown,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            )
        }
    )
}
