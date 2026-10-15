package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.skypulse.weather.ui.glass.liquidGlassSurface
import com.skypulse.weather.ui.glass.surfaceLightField
import com.skypulse.weather.ui.theme.LocalSecondaryPageTheme

/**
 * 次级页面玻璃表面（城市管理 / 预警详情 / 设置）。
 *
 * 次级页底色是 iOS 分组灰而非天空，卡片本身接近实心，
 * 因此光场刻意压得很轻：只留一圈极细的天顶高光、边缘折射微光与内描边，
 * 读起来是「实心卡片上蒙了一层光膜」，而不是「浮在页面上的玻璃砖」。
 *
 * [radius] 必须与 [shape] 的实际圆角一致，否则着色器的高光边会与轮廓错位。
 */
@Composable
fun Modifier.secondaryGlassSurface(
    shape: Shape,
    radius: Dp
): Modifier {
    val page = LocalSecondaryPageTheme.current
    return this
        .clip(shape)
        .background(page.cardBackground)
        .liquidGlassSurface(page.surfaceLightField(radius = radius))
}

/**
 * 浮动玻璃面板（说明类弹窗）表面。
 *
 * 与主页卡片不同，弹窗背后是实时硬件高斯模糊的整屏内容，
 * 因此这里的罩色更透、色散更强，并让光场相位缓慢流动（阻尼流体观感）。
 *
 * @param radius 圆角半径，需与 [shape] 一致
 * @param phase 光场相位秒数，由 [rememberLiquidGlassPhase] 提供
 * @param press 按压进度 0..1
 */
@Composable
fun Modifier.floatingGlassSurface(
    shape: Shape,
    radius: Dp,
    phase: Float = 0f,
    press: Float = 0f
): Modifier {
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = 0.10f))
        .liquidGlassSurface(
            com.skypulse.weather.ui.glass.LiquidGlassParams(
                tint = Color.White.copy(alpha = 0.13f),
                edgeTint = Color.White.copy(alpha = 0.85f),
                specularTint = Color.White.copy(alpha = 0.45f),
                radius = radius,
                luminance = 1.05f,
                bloom = 1.15f,
                dispersion = 0.55f,
                shimmer = 0.85f,
                lightAngleDegrees = 0f,
                press = press
            ),
            phase = phase
        )
}