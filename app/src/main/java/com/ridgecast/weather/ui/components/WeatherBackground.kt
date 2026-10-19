package com.ridgecast.weather.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ridgecast.weather.model.DailyForecast
import com.ridgecast.weather.ui.glass.AtmosphereLightField
import com.ridgecast.weather.util.WeatherUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * 天气背景容器：光场氛围层 + 5 色渐变 + 粒子特效 + 实时模糊源。
 *
 * 结构分两层（这是玻璃卡片能正确模糊的关键）：
 * 1. 模糊源层（haze）：光场氛围层 + 天气渐变 + 粒子特效，被录入离屏图层；
 * 2. 内容层：卡片等 UI，通过 [LocalGlassHazeState] 拿到状态后
 *    以 hazeChild 采样模糊源——内容层自身不会被录入，避免自我模糊反馈。
 *
 * 氛围层提供 ColorOS 16 式的光场底调：天顶受光、地平线反光、四周暗角收拢，
 * 让玻璃卡片边缘的高光有「光从上方来」的物理依据。
 *
 * 省电模式下不提供模糊状态，卡片自动降级为增强底色（见 GlassCard）。
 */
@Composable
fun WeatherBackground(
    skycon: String?,
    daily: DailyForecast? = null,
    wind: WindInfo? = null,
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDay = WeatherUtils.isCurrentlyDay(daily)
    val gradientColors = WeatherUtils.getWeatherGradient(skycon, isDay)

    // 背景色平滑过渡：天气/昼夜切换时逐色插值（渐变列表恒为 5 色），避免硬切。
    // 首次组合时 animateColorAsState 直接取目标值，不会出现入场闪变。
    val animatedColors = gradientColors.mapIndexed { index, color ->
        animateColorAsState(
            targetValue = color,
            animationSpec = tween(durationMillis = 600),
            label = "bg_color_$index"
        ).value
    }

    // 省电模式下关闭实时模糊（haze 在 API<31 等平台会自动使用罩色降级）
    val powerSave = rememberPowerSaveMode()
    val state = hazeState ?: remember { HazeState() }

    Box(modifier = modifier.fillMaxSize()) {
        // 第一层：模糊源（光场氛围 + 渐变 + 粒子）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = state)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = animatedColors,
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // 光场氛围层：与渐变同层叠加，为卡片高光提供光照依据
            LightFieldAtmosphere(
                zenith = animatedColors.firstOrNull() ?: Color.Black,
                horizon = animatedColors.lastOrNull() ?: Color.White,
                isDay = isDay
            )

            // 极淡的等压线纹理：给液态玻璃提供可折射的低频结构。
            // 线条本身几乎不可见，但经过玻璃边缘时会被透镜位移拉出细微弯折。
            IsobarVeil(isDay = isDay)

            // 粒子效果叠加层（不影响内容展示）
            if (showParticles) {
                WeatherEffectOverlay(
                    skycon = skycon,
                    isDay = isDay,
                    wind = wind,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 第二层：内容（卡片经 LocalGlassHazeState 采样上面的模糊源）
        CompositionLocalProvider(
            LocalGlassHazeState provides (if (powerSave) null else state)
        ) {
            content()
        }
    }
}

/**
 * 极淡的等压线纹理（天气语义背景）。
 *
 * 高频细节会被 4dp 模糊抹平，因此这里刻意使用大波长、低对比的曲线：
 * 它们能在玻璃内保留为柔和明暗带，并在折射带产生可见的位移弯折。
 */
@Composable
private fun IsobarVeil(isDay: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val lineColor = Color.White.copy(alpha = if (isDay) 0.115f else 0.08f)
        val stroke = 5f * density
        val rows = 10
        for (i in 0 until rows) {
            val baseY = h * (0.05f + i * 0.11f)
            val amp = h * (0.008f + 0.004f * (i % 3))
            val phase = i * 1.17f
            val path = Path()
            path.moveTo(-w * 0.05f, baseY + kotlin.math.sin(phase) * amp)
            val segments = 5
            for (s in 0 until segments) {
                val x0 = -w * 0.05f + w * 1.1f * s / segments
                val x1 = -w * 0.05f + w * 1.1f * (s + 1) / segments
                val y0 = baseY + kotlin.math.sin(phase + s * 0.9f) * amp
                val y1 = baseY + kotlin.math.sin(phase + (s + 1) * 0.9f) * amp
                val dx = (x1 - x0) * 0.42f
                path.cubicTo(x0 + dx, y0, x1 - dx, y1, x1, y1)
            }
            drawPath(path, lineColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * 光场氛围层：天顶光晕 + 地平线反光 + 四周暗角。
 *
 * 全部为静态画笔且不参与逐帧重绘，因此即使上方跑着粒子特效也不会增加绘制负担。
 */
@Composable
private fun LightFieldAtmosphere(
    zenith: Color,
    horizon: Color,
    isDay: Boolean
) {
    val field = remember(zenith, horizon, isDay) {
        AtmosphereLightField(zenith = zenith, horizon = horizon, isDay = isDay)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(field.verticalWash())
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(field.horizonGlow())
        )
        // 暗角必须按实际绘制尺寸构造：Brush 的中心/半径是像素单位，
        // 用归一化值会让几乎整屏落到暗色端。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = field.vignetteAlpha)
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.42f),
                            radius = field.vignetteRadius(size.width, size.height)
                        )
                    )
                }
        )
    }
}