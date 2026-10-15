package com.skypulse.weather.ui.glass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.skypulse.weather.ui.theme.SecondaryPageColors
import com.skypulse.weather.ui.theme.WeatherTheme

/**
 * 光场参数派生规范。
 *
 * 所有材质参数都由所在区域的既有色板派生，不做逐天气手工调色：
 * 天气色相 → 边缘光与色散色；iOS 次级页色板 → 浅/深两套镜面强度；
 * 因此新增天气类型或切换深浅色时，光场会自动跟随，无需额外维护。
 */

/**
 * 主页玻璃卡片的光场参数。
 *
 * [GlassColors.tint] 已经承担了"模糊层罩色"，本层 tint 刻意压低，
 * 只保留少量体积色，避免把 haze 的通透感压死；高光与色散集中在边缘。
 */
fun WeatherTheme.homeGlassLightField(
    radius: Dp,
    press: Float = 0f
): LiquidGlassParams {
    val horizon = backgroundGradient.lastOrNull() ?: Color.White
    val zenith = backgroundGradient.firstOrNull() ?: Color.Black

    // 边缘光取地平线亮色并向白提亮，保证任何天气下都有可读的轮廓
    val edgeLight = lerp(horizon, Color.White, if (isDay) 0.42f else 0.55f)

    return LiquidGlassParams(
        tint = glass.tint.copy(alpha = glass.tint.alpha * 0.55f),
        edgeTint = edgeLight.copy(alpha = 0.88f),
        specularTint = Color.White.copy(alpha = if (isDay) 0.34f else 0.20f),
        radius = radius,
        luminance = 1.0f,
        bloom = 1.0f,
        dispersion = 0.45f,
        shimmer = 0.5f,
        lightAngleDegrees = 0f,
        press = press
    )
}

/**
 * 次级页面（设置 / 城市管理 / 预警详情）分组卡片的光场参数。
 *
 * 次级页底色是 iOS 分组灰而非天空，卡片本身接近实心，
 * 因此这里把光场压得很轻：只留一圈极细的天顶高光与微弱内描边，
 * 读起来是「玻璃上贴了一层光膜」而不是「一块浮起来的玻璃砖」。
 */
fun SecondaryPageColors.surfaceLightField(
    radius: Dp,
    press: Float = 0f
): LiquidGlassParams = if (isDark) {
    LiquidGlassParams(
        tint = cardBackground.copy(alpha = 0.22f),
        edgeTint = Color.White.copy(alpha = 0.60f),
        specularTint = Color.White.copy(alpha = 0.14f),
        radius = radius,
        luminance = 1.0f,
        bloom = 0.85f,
        dispersion = 0.32f,
        shimmer = 0.30f,
        lightAngleDegrees = 0f,
        press = press
    )
} else {
    LiquidGlassParams(
        tint = Color.White.copy(alpha = 0.34f),
        edgeTint = Color.White.copy(alpha = 0.92f),
        specularTint = Color.White.copy(alpha = 0.50f),
        radius = radius,
        luminance = 1.0f,
        bloom = 0.70f,
        dispersion = 0.26f,
        shimmer = 0.26f,
        lightAngleDegrees = 0f,
        press = press
    )
}

/**
 * 天气背景光场氛围层。
 *
 * 全屏尺寸远超圆角 SDF 的有效范围（圆角半径不能超过短边一半），
 * 因此背景层不走上层着色器，改用等价的大尺度亮度分层：
 * 天顶受光 + 地平线反光 + 四周暗角收拢，
 * 视觉上提供 ColorOS 16 的光场氛围，也为玻璃卡片边缘的高光提供「光从上方来」的依据。
 *
 * 暗角必须在 [androidx.compose.ui.draw.drawBehind] 内按实际尺寸构造，
 * Brush 的中心与半径使用像素坐标，提前按归一化值构造会导致整个画面被压暗。
 */
data class AtmosphereLightField(
    val zenith: Color,
    val horizon: Color,
    val isDay: Boolean
) {
    /** 天顶受光：自上而下的纵向层理。 */
    fun verticalWash(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to zenith.copy(alpha = if (isDay) 0.30f else 0.16f),
            0.35f to zenith.copy(alpha = if (isDay) 0.10f else 0.05f),
            1.0f to Color.Transparent
        )
    )

    /** 地平线反光：底部一道柔和的天气色回弹。 */
    fun horizonGlow(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.85f to Color.Transparent,
            1.0f to horizon.copy(alpha = if (isDay) 0.22f else 0.12f)
        )
    )

    /** 暗角最大不透明度。 */
    val vignetteAlpha: Float get() = if (isDay) 0.10f else 0.22f
}