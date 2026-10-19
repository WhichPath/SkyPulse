package com.ridgecast.weather.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ridgecast.weather.ui.theme.LocalWeatherTheme
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem

/**
 * 玻璃卡片：液态玻璃版本（IT之家风格）。
 *
 * 背景采样、模糊与 AGSL 折射全部由 [liquidGlass] 在独立图层完成，
 * 卡片内容（文字/图表）不再经过 shader，保持锐利。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = SkyPulseDesignSystem.Radius.card,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalWeatherTheme.current
    val glass = theme.glass
    val hazeState = LocalGlassHazeState.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                radius = radius,
                isDark = !glass.isLight,
                hazeState = hazeState,
                backgroundColor = theme.backgroundGradient[theme.backgroundGradient.size / 2]
            ),
        content = content
    )
}
