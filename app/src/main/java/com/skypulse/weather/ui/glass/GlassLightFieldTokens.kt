package com.skypulse.weather.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.SecondaryPageColors
import com.skypulse.weather.ui.theme.WeatherTheme

/**
 * 光场参数派生规范。
 *
 * 所有材质参数都由所在区域的既有色板派生，不做逐天气手工调色：
 * 天气色相 → 玻璃底色与边缘光；iOS 次级页色板 → 浅/深两套镜面强度；
 * 因此新增天气类型或切换深浅色时，光场会自动跟随，无需额外维护。
 *
 * 关键约束：**玻璃底色必须始终带有可辨识的颜色**（哪怕 alpha 很低）。
 * v4.0.2 实测曾因底色层无色而整块退化为灰黑板，此后所有派生函数都显式给出带色底色。
 */

/**
 * 主页玻璃卡片的光场参数。
 *
 * 底色直接复用天气派生的 [com.skypulse.weather.ui.theme.GlassColors.tint]，
 * 保证"玻璃有颜色"；边缘高光取地平线色向白提亮，夜间进一步提亮以保证轮廓可读。
 */
fun WeatherTheme.homeGlassLightField(): GlassLightField {
    val horizon = backgroundGradient.lastOrNull() ?: Color.White
    val edgeLight = lerp(horizon, Color.White, if (isDay) 0.65f else 0.75f)

    return GlassLightField(
        base = glass.tint,
        bloom = Color.White,
        edge = edgeLight,
        edgeWidth = 1.0.dp,
        bloomStrength = if (isDay) 0.85f else 0.50f,
        edgeStrength = if (isDay) 0.90f else 0.75f
    )
}

/**
 * 次级页面（设置 / 城市管理 / 预警详情）分组卡片的光场参数。
 *
 * 遵循 Material 3 Expressive 分段容器标准。
 */
fun SecondaryPageColors.surfaceLightField(): GlassLightField =
    if (isDark) {
        GlassLightField(
            // M3 深色模式 Surface Container High
            base = Color(0xFF1C1E21).copy(alpha = 0.55f),
            bloom = Color.White,
            edge = Color.White.copy(alpha = 0.85f),
            edgeWidth = 1.0.dp,
            bloomStrength = 0.35f,
            edgeStrength = 0.45f
        )
    } else {
        GlassLightField(
            // M3 浅色模式 Surface Container
            base = Color.White.copy(alpha = 0.68f),
            bloom = Color.White,
            edge = Color.White.copy(alpha = 0.95f),
            edgeWidth = 1.0.dp,
            bloomStrength = 0.45f,
            edgeStrength = 0.60f
        )
    }

/**
 * 浮动玻璃面板（说明类弹窗）光场参数。
 *
 * 弹窗背后是整屏实时硬件高斯模糊，因此罩色更透、边缘折射更强。
 */
fun floatingModalLightField(): GlassLightField = GlassLightField(
    base = Color.White.copy(alpha = 0.17f),
    bloom = Color.White,
    sheen = Color.White,
    edge = Color.White,
    edgeWidth = 1.4.dp,
    bloomStrength = 0.85f,
    sheenStrength = 0.90f,
    edgeStrength = 1.15f
)

/**
 * 次级页面背景：与主页同源的天气渐变，按深浅色模式调整明度。
 *
 * 浅色模式大幅向白靠拢（保持 14:1 以上文字对比度），深色模式向黑靠拢，
 * 两者的**色相都来自当前天气**，因此设置页能看出"现在是什么天气"，
 * 又不会牺牲深色模式的可读性。
 *
 * @param mixToTarget 向目标色靠拢的比例；0 = 原样，1 = 纯目标色
 */
fun weatherPageBackground(
    weatherGradient: List<Color>,
    isDark: Boolean
): Brush {
    val target = if (isDark) Color.Black else Color.White
    val amount = if (isDark) 0.52f else 0.86f
    val colors = weatherGradient.map { lerp(it, target, amount) }
    return Brush.verticalGradient(
        colors = colors,
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )
}

/**
 * 天气背景光场氛围层。
 *
 * 全屏氛围：天顶受光 + 地平线反光 + 四周暗角，
 * 为玻璃卡片边缘的高光提供"光从上方来"的物理依据。
 *
 * 暗角必须在 [androidx.compose.ui.draw.drawBehind] 内按实际尺寸构造——
 * [Brush.radialGradient] 的 center/radius 是**像素**坐标，用归一化值会让几乎整屏落到暗色端。
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