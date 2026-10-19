package com.ridgecast.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ridgecast.weather.model.DailyForecast
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 屏幕空间玻璃高光的光源状态。
 *
 * angleDegrees 是 HighlightStyle.Default 的轴向（度），框架会用 abs(dot)
 * 在轴的两侧各形成一条内壁反射高光；这是玻璃双瓣高光，不是固定描边。
 * intensity 是 0..1 的强度缩放，夜晚/月亮主导时降低。
 */
data class GlassLightState(
    val angleDegrees: Float = 45f,
    val intensity: Float = 1f,
    val isDaylight: Boolean = true,
    val source: String = "default"
)

val LocalGlassLight = compositionLocalOf { GlassLightState() }

/**
 * 依据当前时间与当天 astro 计算太阳/月亮主导的高光。
 *
 * - 白天：太阳从东（右）升到西（左），高光轴向随之旋转，正午最强；
 * - 夜晚：优先使用月升/月落，月亮高光更弱、偏冷；
 * - 无 astro 数据时退化为 06:00-18:00 的近似日光。
 *
 * 每 60 秒更新一次，不会每帧重组所有玻璃。
 */
@Composable
fun rememberGlassLight(daily: DailyForecast?): GlassLightState {
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(daily) {
        while (isActive) {
            now = LocalTime.now()
            delay(60_000L)
        }
    }

    return remember(daily, now) { computeGlassLight(daily, now) }
}

private fun computeGlassLight(daily: DailyForecast?, now: LocalTime): GlassLightState {
    val astro = daily?.astro?.firstOrNull()
    val sunrise = parseMinutes(astro?.sunrise?.time) ?: (6 * 60)
    val sunset = parseMinutes(astro?.sunset?.time) ?: (18 * 60)
    val nowMinutes = now.hour * 60 + now.minute

    if (sunset > sunrise && nowMinutes in sunrise..sunset) {
        val progress = ((nowMinutes - sunrise).toFloat() / (sunset - sunrise))
            .coerceIn(0f, 1f)
        val altitude = sin(PI * progress).toFloat().coerceIn(0f, 1f)
        return GlassLightState(
            angleDegrees = skyAngleDegrees(progress),
            intensity = 0.60f + 0.40f * altitude,
            isDaylight = true,
            source = "sun"
        )
    }

    val moonrise = parseMinutes(astro?.moonrise?.time)
    val moonset = parseMinutes(astro?.moonset?.time)
    if (moonrise != null && moonset != null && moonset > moonrise &&
        nowMinutes in moonrise..moonset
    ) {
        val progress = ((nowMinutes - moonrise).toFloat() / (moonset - moonrise))
            .coerceIn(0f, 1f)
        val altitude = sin(PI * progress).toFloat().coerceIn(0f, 1f)
        return GlassLightState(
            angleDegrees = skyAngleDegrees(progress),
            intensity = 0.28f + 0.22f * altitude,
            isDaylight = false,
            source = "moon"
        )
    }

    // 夜间无月亮时段：按日落到次日日出计算，强度更低
    val nightStart = sunset
    val nightEnd = sunrise
    if (nightEnd < nightStart) {
        val total = (24 * 60 - nightStart) + nightEnd
        val elapsed = if (nowMinutes >= nightStart) {
            nowMinutes - nightStart
        } else {
            (24 * 60 - nightStart) + nowMinutes
        }
        val progress = (elapsed.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        val altitude = sin(PI * progress).toFloat().coerceIn(0f, 1f)
        return GlassLightState(
            angleDegrees = skyAngleDegrees(progress),
            intensity = 0.22f + 0.16f * altitude,
            isDaylight = false,
            source = "night"
        )
    }

    return GlassLightState(
        angleDegrees = 45f,
        intensity = 0.55f,
        isDaylight = false,
        source = "fallback"
    )
}

/** 0 = 东方地平线（右），0.5 = 天顶（上），1 = 西方地平线（左）。 */
private fun skyAngleDegrees(progress: Float): Float {
    val x = cos(PI * progress)
    val y = -sin(PI * progress)
    return (atan2(y, x) * 180.0 / PI).toFloat()
}

private fun parseMinutes(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val parts = value.split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
