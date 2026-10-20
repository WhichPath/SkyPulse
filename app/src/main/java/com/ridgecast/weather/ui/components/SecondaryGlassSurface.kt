package com.ridgecast.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ridgecast.weather.ui.glass.GlassStyles
import com.ridgecast.weather.ui.theme.LocalSecondaryPageTheme

/**
 * 次级页面玻璃表面（城市管理 / 预警详情 / 设置）。
 *
 * 统一使用 Backdrop 的实时背景图层；搜索栏、设置分组、预警卡片
 * 不再各自实现材质。
 *
 * 材质参数全部来自 [GlassStyles]，本文件只负责"选哪套样式"。
 */
@Composable
fun Modifier.secondaryGlassSurface(radius: Dp): Modifier {
    val page = LocalSecondaryPageTheme.current
    return this.liquidGlass(
        radius = radius,
        isDark = page.isDark,
        backgroundColor = page.cardBackground,
        style = GlassStyles.Secondary
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
        style = GlassStyles.Floating
    )
}
