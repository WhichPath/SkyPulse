package com.ridgecast.weather.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridgecast.weather.ui.theme.LocalWeatherTheme
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem

/**
 * 主页玻璃卡片：统一走 v1.0.5 的 Backdrop 液态玻璃框架。
 *
 * 背景由 WeatherBackground 的 LayerBackdrop 实时提供；
 * 卡片内容始终在玻璃之上，保持锐利。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = SkyPulseDesignSystem.Radius.card,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalWeatherTheme.current
    val glass = theme.glass
    val isDark = !glass.isLight
    val tint = if (isDark) Color(0xFF0A0B0D) else Color.Black

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                radius = radius,
                isDark = isDark,
                backgroundColor = tint,
                tintAlpha = if (isDark) 0.16f else 0.08f,
                blurRadius = 8.dp,
                refractionHeight = 18.dp,
                refractionAmount = 22.dp
            ),
        content = content
    )
}
