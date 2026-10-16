package com.skypulse.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.skypulse.weather.ui.glass.floatingModalLightField
import com.skypulse.weather.ui.glass.glassLightField
import com.skypulse.weather.ui.glass.surfaceLightField
import com.skypulse.weather.ui.theme.LocalSecondaryPageTheme

/**
 * 次级页面玻璃表面（城市管理 / 预警详情 / 设置）。
 *
 * 次级页底色为天气着色的渐变（见 `weatherPageBackground`），卡片改为半透玻璃，
 * 让底色透上来形成「液态玻璃底部有颜色」的效果。
 * 浅色模式用乳白玻璃承载深色文字，深色模式用压暗玻璃承载浅色文字，
 * 两种模式都保证足够的文字对比度。
 */
@Composable
fun Modifier.secondaryGlassSurface(radius: Dp): Modifier {
    val page = LocalSecondaryPageTheme.current
    val field = page.surfaceLightField()
    return this
        .clip(ContinuousCornerShape(radius))
        .glassLightField(radius = radius, field = field)
}

/**
 * 浮动玻璃面板（说明类弹窗）表面。
 *
 * 背后是整屏实时硬件高斯模糊，因此罩色更透、边缘折射高光更强。
 */
@Composable
fun Modifier.floatingGlassSurface(radius: Dp): Modifier {
    val field = floatingModalLightField()
    return this
        .clip(ContinuousCornerShape(radius))
        .glassLightField(radius = radius, field = field)
}