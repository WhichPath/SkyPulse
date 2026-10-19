package com.ridgecast.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ridgecast.weather.ui.theme.LocalSecondaryPageTheme

/**
 * 次级页面玻璃表面（城市管理 / 预警详情 / 设置）。
 *
 * 真实模糊背景 + AGSL 折射由 [liquidGlass] 在独立图层完成，
 * 卡片内容保持锐利；无 Haze 源时自动降级为半透明底色玻璃。
 */
@Composable
fun Modifier.secondaryGlassSurface(radius: Dp): Modifier {
    val page = LocalSecondaryPageTheme.current
    val hazeState = LocalGlassHazeState.current
    return this.liquidGlass(
        radius = radius,
        isDark = page.isDark,
        hazeState = hazeState,
        backgroundColor = page.cardBackground
    )
}

/**
 * 浮动玻璃面板（说明类弹窗）表面。
 */
@Composable
fun Modifier.floatingGlassSurface(radius: Dp): Modifier {
    val page = LocalSecondaryPageTheme.current
    val hazeState = LocalGlassHazeState.current
    return this.liquidGlass(
        radius = radius,
        isDark = page.isDark,
        hazeState = hazeState,
        backgroundColor = page.cardBackground
    )
}
