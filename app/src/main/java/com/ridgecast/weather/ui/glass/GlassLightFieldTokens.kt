package com.ridgecast.weather.ui.glass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ridgecast.weather.util.skyGradientColorStops

/**
 * 次级页面背景：与主页同源的天气渐变，按深浅色模式调整明度。
 *
 * 浅色模式大幅向白靠拢（保持文字对比度），深色模式向黑靠拢，
 * 两者的**色相都来自当前天气**，因此设置页能看出"现在是什么天气"，
 * 又不会牺牲深色模式的可读性。
 */
fun weatherPageBackground(
    weatherGradient: List<Color>,
    isDark: Boolean
): Brush {
    val target = if (isDark) Color.Black else Color.White
    val amount = if (isDark) 0.52f else 0.86f
    val colors = weatherGradient.map { lerp(it, target, amount) }
    return Brush.verticalGradient(
        // 与主页共用 SkyGradientStops 非等距锚点，避免次级页与主屏的
        // 「天顶压深」口径不一致。
        colorStops = skyGradientColorStops(colors),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )
}

/**
 * 天气背景光场氛围层。
 *
 * 全屏氛围：天顶受光 + 地平线反光 + 四周暗角，
 * 为液态玻璃边缘的高光提供"光从上方来"的物理依据。
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

    /** 暗角半径：以长边为基准，保证四角都收拢。 */
    fun vignetteRadius(width: Float, height: Float): Float =
        maxOf(width, height) * 0.72f
}
