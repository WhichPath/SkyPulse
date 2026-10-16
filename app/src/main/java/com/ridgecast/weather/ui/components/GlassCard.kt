package com.ridgecast.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ridgecast.weather.ui.glass.glassLightField
import com.ridgecast.weather.ui.glass.homeGlassLightField
import com.ridgecast.weather.ui.theme.LocalWeatherTheme
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * 玻璃卡片（无边框设计）。
 *
 * 材质三层：
 * 1. 底层：haze 实时模糊（20dp 半径 + 微噪声防色带），
 *    让背后的雨丝/闪电/星空透过卡片隐约可见；
 *    低版本 / 省电模式自动降级为增强底色（haze fallbackTint 或纯底色）；
 * 2. 中间层：[GlassColors.tint] 罩色——由天气色相派生（白天提亮、雨雪夜晚压暗），
 *    **保证玻璃本身带色**，不会因缺少颜色而退化成灰黑板；
 * 3. 顶层：光场——天顶光晕、各向异性斜面光泽、边缘折射高光（见 [homeGlassLightField]）。
 *
 * 光场参数由天气主题自动派生，新增天气类型时无需手工调色。
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
    val shape = remember(radius) { ContinuousCornerShape(radius, exponent = 4.2f) }
    val lightField = theme.homeGlassLightField()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = theme.backgroundGradient[theme.backgroundGradient.size / 2],
                            tint = HazeTint(glass.tint),
                            blurRadius = 16.dp,
                            noiseFactor = 0.05f,
                            fallbackTint = HazeTint(glass.tintFallback)
                        )
                    )
                } else {
                    Modifier.background(glass.tintFallback)
                }
            )
            .glassLightField(radius = radius, field = lightField),
        content = content
    )
}