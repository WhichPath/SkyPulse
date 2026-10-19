package com.ridgecast.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ridgecast.weather.ui.theme.LocalSecondaryPageTheme

/**
 * 次级页面玻璃表面（城市管理 / 预警详情 / 设置）。
 *
 * 统一使用 Backdrop 的实时背景图层；搜索栏、设置分组、预警卡片
 * 不再各自实现材质。
 */
@Composable
fun Modifier.secondaryGlassSurface(radius: Dp): Modifier {
    val page = LocalSecondaryPageTheme.current
    return this.liquidGlass(
        radius = radius,
        isDark = page.isDark,
        backgroundColor = page.cardBackground,
        tintAlpha = if (page.isDark) 0.28f else 0.22f,
        blurRadius = 8.dp,
        refractionHeight = 16.dp,
        refractionAmount = 20.dp
    )
}

/**
 * 浮动玻璃面板（说明类弹窗）表面。
 *
 * 弹窗背后是整屏实时背景，折射带更宽、玻璃更透。
 */
@Composable
fun Modifier.floatingGlassSurface(radius: Dp): Modifier {
    val page = LocalSecondaryPageTheme.current
    return this.liquidGlass(
        radius = radius,
        isDark = page.isDark,
        backgroundColor = page.cardBackground,
        tintAlpha = if (page.isDark) 0.26f else 0.20f,
        blurRadius = 10.dp,
        refractionHeight = 24.dp,
        refractionAmount = 28.dp
    )
}
