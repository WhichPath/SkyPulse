package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.glass.homeGlassLightField
import com.skypulse.weather.ui.glass.liquidGlassSurface
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * 玻璃卡片（无边框设计）。
 *
 * 材质三层（v4.0.2 液态玻璃光场）：
 * 1. 底层：haze 实时模糊（20dp 半径 + 微噪声防色带），
 *    让背后的雨丝/闪电/星空透过卡片隐约可见；
 *    低版本 / 省电模式自动降级为增强底色（haze fallbackTint 或纯底色）；
 * 2. 中间层：[GlassColors.tint] 罩色——由天气色相派生（白天提亮、雨雪夜晚压暗）；
 * 3. 顶层：2026 液态玻璃光场（AGSL）——各向异性天顶斜面高光、
 *    圆角边缘折射色散、动态亮度分层与透镜中心柔光。
 *
 * 光场参数由天气主题自动派生（见 [homeGlassLightField]），因此新增天气类型时
 * 无需手工调色。着色器不可用的机型自动降级为渐变罩色，不影响可用性。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalWeatherTheme.current
    val glass = theme.glass
    val hazeState = LocalGlassHazeState.current
    val radius = SkyPulseDesignSystem.Radius.card
    val shape = RoundedCornerShape(radius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (hazeState != null) {
                    // 已在外层 clip(shape)，使用非弃用的 hazeChild(state, style) 重载。
                    // 注意：haze 1.0.2 模糊路径（API 32+）强制要求 backgroundColor，
                    // 作为模糊层底色填充（缺省会抛 IllegalArgumentException）。
                    // 取天气渐变中段不透明色，保证模糊边缘与天空无缝融合。
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = theme.backgroundGradient[theme.backgroundGradient.size / 2],
                            tint = HazeTint(glass.tint),
                            blurRadius = 20.dp,
                            noiseFactor = 0.08f,
                            fallbackTint = HazeTint(glass.tintFallback)
                        )
                    )
                } else {
                    Modifier.background(glass.tintFallback)
                }
            )
            // 光场必须叠在 clip 之后：着色器内部同样按圆角矩形做 SDF 裁剪，
            // 两者一致才能让高光边严丝合缝地贴在卡片轮廓上。
            .liquidGlassSurface(theme.homeGlassLightField(radius = radius)),
        content = content
    )
}