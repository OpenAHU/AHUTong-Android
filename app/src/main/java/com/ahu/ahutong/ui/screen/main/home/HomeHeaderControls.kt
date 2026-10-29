package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.liquidFloatingSurface
import com.kyant.backdrop.Backdrop

/**
 * 标题栏常驻控件（天气胶囊 / 扫码钮）的统一高度。
 *
 * 两个控件并列放置，必须同高才能形成一条"控件行"，不再一高一低。
 */
internal val HomeHeaderControlHeight = 36.dp

/**
 * 标题栏常驻控件的材质 = 底部导航舱的同一份实现（[liquidFloatingSurface]）。
 *
 * 刻意不再单独调参：vibrancy + floating 档模糊 + floating 档折射 + 半透明容器色，
 * 无描边、无额外投影。backdrop 由调用方传入「壁纸 + 滚动内容」组合层，
 * 控件底下透出的是滚过去的卡片而不是纯背景色。
 */
@Composable
fun HomeHeaderGlass(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
): Modifier = if (backdrop != null) modifier.liquidFloatingSurface(backdrop) else modifier

/**
 * 主页标题栏的扫码入口。
 *
 * 36dp × ContinuousCapsule = 正圆；与天气胶囊同高、同材质。
 */
@Composable
fun HomeScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null
) {
    Box(
        modifier = HomeHeaderGlass(backdrop, modifier)
            .size(HomeHeaderControlHeight)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // iconpark「扫码」线性图标（与导航栏、小工具同一套矢量语言），随容器 tint 变色
        Icon(
            painter = painterResource(R.drawable.ic_nav_scan),
            contentDescription = "扫一扫",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}