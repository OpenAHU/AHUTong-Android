package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.AppHeaderIconButton
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
 * 主页标题栏的相机入口，与天气胶囊同高，样式由当前组件包提供。
 */
@Composable
fun HomeScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null
) {
    AppHeaderIconButton(
        imageVector = ImageVector.vectorResource(R.drawable.ic_nav_camera),
        contentDescription = "相机",
        onClick = onClick,
        modifier = modifier.size(HomeHeaderControlHeight),
        backdrop = backdrop
    )
}
