package com.ahu.ahutong.ui.screen.setup

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ahu.ahutong.feature.login.R
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1

@Composable
fun Splash() {
    val context = LocalContext.current
    val appIconBitmap = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            drawable.toBitmap()
        }
        bitmap.asImageBitmap()
    }

    AhuScreen(
        scrollable = false,
        clearBottomNav = false,
        verticalArrangement = Arrangement.spacedBy(AhuDimens.SectionSpacing, Alignment.CenterVertically),
    ) {
        Image(
            bitmap = appIconBitmap,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(ContinuousCapsule)
                .background(100.n1)
                .padding(4.dp)
                .size(128.dp)
                .clip(ContinuousCapsule)
                .scale(1.5f)
        )
        Text(
            text = stringResource(id = R.string.app_name),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = AhuColors.onSurface,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}
