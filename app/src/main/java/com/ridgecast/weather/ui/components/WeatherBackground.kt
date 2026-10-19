package com.ridgecast.weather.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import com.ridgecast.weather.model.DailyForecast
import com.ridgecast.weather.ui.glass.AtmosphereLightField
import com.ridgecast.weather.util.WeatherUtils
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 天气背景容器：光场氛围层 + 5 色渐变 + 粒子特效 + 液态玻璃背景源。
 *
 * 结构分两层：
 * 1. 背景源层：光场氛围 + 天气渐变 + 粒子，经 [layerBackdrop] 录制成实时图层；
 * 2. 内容层：卡片等 UI 通过 [LocalGlassBackdrop] 消费该图层。
 *
 * 玻璃面板移动/页面滚动时，Backdrop 会按 LayoutCoordinates 重新对齐采样，
 * 背景纹理不会“粘”在面板上。
 *
 * 省电模式下不提供背景源，卡片自动降级为半透明表面。
 */
@Composable
fun WeatherBackground(
    skycon: String?,
    daily: DailyForecast? = null,
    wind: WindInfo? = null,
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    backdrop: LayerBackdrop? = null,
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

    // 省电模式下不提供玻璃背景源，卡片自动降级
    val powerSave = rememberPowerSaveMode()
    val glassBackdrop = backdrop ?: rememberLayerBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        // 第一层：实时背景源（光场氛围 + 渐变 + 粒子）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop)
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

        // 第二层：内容（卡片经 LocalGlassBackdrop 采样上面的实时背景）
        CompositionLocalProvider(
            LocalGlassBackdrop provides (if (powerSave) null else glassBackdrop)
        ) {
            content()
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