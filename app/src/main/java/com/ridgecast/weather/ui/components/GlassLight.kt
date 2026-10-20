package com.ridgecast.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ridgecast.weather.model.AstronomySnapshot
import com.ridgecast.weather.model.DailyAstro
import com.ridgecast.weather.model.DailyForecast
import com.ridgecast.weather.ui.glass.BlackbodyColor
import com.ridgecast.weather.ui.glass.GlassLightState
import com.ridgecast.weather.ui.glass.GlassLighting
import com.ridgecast.weather.ui.glass.LightKind
import com.ridgecast.weather.ui.glass.LightSource
import com.ridgecast.weather.ui.glass.MoonIllumination
import com.ridgecast.weather.ui.glass.SkyPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * 屏幕空间玻璃高光的**光源状态**（v1.0.7）。
 *
 * 与 v1.0.5 的区别：不再是"单一光源 + 一个强度标量"，而是**太阳 + 月亮两个
 * 真实光源**，各自拥有高度角、方位角、照度与色温。
 *
 * ## 数据优先级
 * 1. **接口优先**：和风天文接口给出太阳高度角/方位角与月相被照亮比例；
 * 2. **本地兜底**：接口失败、超时或返回非数值时，用本地 NOAA/Meeus 算法重算；
 * 3. **无坐标兜底**：连坐标都没有时，退回按 astro 日出日落 / 月升月落的屏幕弧线估算。
 *
 * 没有任何可见光源（太阳与月亮都不在地平线以上）时返回空状态，
 * 面板会**完全不绘制边缘高光**。
 */
val LocalGlassLight = compositionLocalOf { GlassLightState() }

/**
 * 每分钟一次的时钟源。
 *
 * 单独抽出来是为了让**同一屏内多个城市共用一个心跳**：
 * 城市分页里每页都要算自己的光照，如果每页各起一个定时器，
 * 收藏 5 个城市就是 5 个定时器在轮询。
 */
@Composable
fun rememberNowMillis(intervalMillis: Long = 60_000L): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMillis) {
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(intervalMillis)
        }
    }
    return nowMillis
}

/**
 * 计算当前玻璃光照状态。
 *
 * @param daily 当天逐日预报（提供 astro 日出日落 / 月升月落，作为最后兜底）
 * @param astronomy 天文接口快照（可空）
 * @param latitude 城市纬度（可空）
 * @param longitude 城市经度（可空）
 * @param nowMillis 当前时刻；缺省时使用每分钟自走的心跳
 */
@Composable
fun rememberGlassLight(
    daily: DailyForecast?,
    astronomy: AstronomySnapshot? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    nowMillis: Long = rememberNowMillis()
): GlassLightState {
    return remember(daily, astronomy, latitude, longitude, nowMillis) {
        computeGlassLight(daily, astronomy, latitude, longitude, nowMillis)
    }
}

internal fun computeGlassLight(
    daily: DailyForecast?,
    astronomy: AstronomySnapshot?,
    latitude: Double?,
    longitude: Double?,
    nowMillis: Long
): GlassLightState {
    val astro = daily?.astro?.firstOrNull()
    val sun = computeSun(astro, astronomy, latitude, longitude, nowMillis)
    val moon = computeMoon(astro, astronomy, latitude, longitude, nowMillis)
    if (sun != null || moon != null) {
        return GlassLightState(sun = sun, moon = moon)
    }
    // 完全没有数据源（首次启动、引导页、缓存为空）：退化为默认日光，
    // 避免玻璃面板变成"没有高光的死板方块"。
    // 注意：这里与"太阳月亮都不在地平线以上"是两回事——后者应保持无高光。
    val hasAnySource = daily != null || astronomy != null ||
        (latitude != null && longitude != null)
    return if (hasAnySource) {
        GlassLightState(sun = null, moon = null)
    } else {
        GlassLightState(sun = defaultDaylight(), moon = null)
    }
}

/** 无数据源时的默认日光（沿用 v1.0.5 的兜底观感）。 */
private fun defaultDaylight(): LightSource = LightSource(
    kind = LightKind.SUN,
    angleDegrees = 45f,
    intensity = 0.55f,
    color = BlackbodyColor.fromColorTemperature(5778.0),
    colorTemperatureK = 5778,
    illuminanceLux = 0.0
)

// ------------------------------------------------------------------
// 太阳
// ------------------------------------------------------------------

private fun computeSun(
    astro: DailyAstro?,
    astronomy: AstronomySnapshot?,
    latitude: Double?,
    longitude: Double?,
    nowMillis: Long
): LightSource? {
    // 1) 接口优先
    val apiElevation = astronomy?.solarElevationDegrees
    val apiAzimuth = astronomy?.solarAzimuthDegrees
    if (apiElevation != null && apiAzimuth != null &&
        apiElevation.isFinite() && apiAzimuth.isFinite()
    ) {
        return GlassLighting.sunSource(apiElevation, apiAzimuth)
    }

    // 2) 本地 NOAA 计算
    if (latitude != null && longitude != null) {
        val position = SkyPosition.sun(nowMillis, latitude, longitude)
        return GlassLighting.sunSource(position.elevationDegrees, position.azimuthDegrees)
    }

    // 3) 无坐标：按日出/日落时刻的屏幕弧线估算
    val sunrise = parseMinutes(astro?.sunrise?.time)
    val sunset = parseMinutes(astro?.sunset?.time)
    if (sunrise == null || sunset == null || sunset <= sunrise) return null
    val nowMinutes = localMinutesOfDay()
    if (nowMinutes !in sunrise..sunset) return null
    val progress = (nowMinutes - sunrise).toFloat() / (sunset - sunrise).toFloat()
    // 以正弦弧近似太阳高度角
    val elevation = 90.0 * sin(PI * progress)
    val lux = GlassLighting.sunIlluminanceLux(elevation)
    val intensity = GlassLighting.perceived(lux)
    if (intensity <= 0f) return null
    return LightSource(
        kind = LightKind.SUN,
        angleDegrees = GlassLighting.arcAngleDegrees(progress),
        intensity = intensity,
        color = BlackbodyColor.sunlight(elevation),
        colorTemperatureK = 0,
        illuminanceLux = lux
    )
}

// ------------------------------------------------------------------
// 月亮
// ------------------------------------------------------------------

private fun computeMoon(
    astro: DailyAstro?,
    astronomy: AstronomySnapshot?,
    latitude: Double?,
    longitude: Double?,
    nowMillis: Long
): LightSource? {
    // 被照亮比例：接口优先 → 月相名称查表 → 本地月相角
    val illumination = astronomy?.moonIllumination
        ?: MoonIllumination.fromPhaseName(astro?.moonPhase)
        ?: run {
            if (latitude != null && longitude != null) {
                SkyPosition.moon(nowMillis, latitude, longitude).illuminatedFraction
            } else {
                null
            }
        }
    if (illumination == null) return null

    // 位置
    if (latitude != null && longitude != null) {
        val position = SkyPosition.moon(nowMillis, latitude, longitude)
        return GlassLighting.moonSource(
            elevationDegrees = position.elevationDegrees,
            azimuthDegrees = position.azimuthDegrees,
            illuminatedFraction = illumination
        )
    }

    // 无坐标：按月升 → 月中天 → 月落的三段弧线估算
    val moonrise = parseMinutes(astro?.moonrise?.time)
    val moonset = parseMinutes(astro?.moonset?.time)
    val transit = parseMinutes(astro?.moonTransit?.time)
    if (moonrise == null || moonset == null) return null
    val progress = moonArcProgress(localMinutesOfDay(), moonrise, transit, moonset)
        ?: return null
    val elevation = 70.0 * sin(PI * progress)
    val lux = GlassLighting.moonIlluminanceLux(elevation, illumination)
    val intensity = GlassLighting.perceived(lux)
    if (intensity <= 0.02f) return null
    return LightSource(
        kind = LightKind.MOON,
        angleDegrees = GlassLighting.arcAngleDegrees(progress),
        intensity = intensity,
        color = BlackbodyColor.fromColorTemperature(BlackbodyColor.MOON_APPEARANCE_TEMPERATURE_K),
        colorTemperatureK = BlackbodyColor.MOON_APPEARANCE_TEMPERATURE_K.toInt(),
        illuminanceLux = lux
    )
}

/**
 * 月亮在月升→中天→月落之间的弧线进度 0..1。
 *
 * 月亮每天推迟约 50 分钟，跨午夜时月落时间会早于月升（说明这一轮跨到了次日），
 * 这里统一把月落补到次日再计算，避免出现负进度。
 */
private fun moonArcProgress(
    nowMinutes: Int,
    moonrise: Int,
    transit: Int?,
    moonset: Int
): Float? {
    val rise = moonrise
    val set = if (moonset > moonrise) moonset else moonset + 24 * 60
    val mid = transit?.let { if (it < rise) it + 24 * 60 else it }
    val total = (set - rise).coerceAtLeast(1)
    val elapsed = if (nowMinutes < rise) nowMinutes + 24 * 60 - rise else nowMinutes - rise
    if (elapsed < 0 || elapsed > total) return null
    val raw = if (mid != null && mid in rise..set && mid > rise) {
        if (elapsed <= mid - rise) {
            elapsed.toFloat() / (mid - rise).toFloat() * 0.5f
        } else {
            0.5f + (elapsed - (mid - rise)).toFloat() / (set - mid).toFloat() * 0.5f
        }
    } else {
        elapsed.toFloat() / total.toFloat()
    }
    return raw.coerceIn(0f, 1f)
}

// ------------------------------------------------------------------
// 工具
// ------------------------------------------------------------------

private fun localMinutesOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/** 解析 "HH:mm"，非法值返回 null。 */
private fun parseMinutes(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val parts = value.trim().split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
