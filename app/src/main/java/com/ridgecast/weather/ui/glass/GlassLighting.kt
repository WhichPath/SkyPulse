package com.ridgecast.weather.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * 玻璃边缘高光的**物理光源模型**。
 *
 * ## 物理量与显示量的换算
 * 真实场景里太阳与月亮的照度相差约 **5.5 个数量级**
 * （正午晴空直射日光 ≈ 1.2×10⁵ lux，满月中天 ≈ 0.25 lux，比值 ≈ 1/380 000，
 * 与 Wikipedia「满月视星等仅为太阳的 1/380000」一致）。
 * 这种量级差在屏幕上**无法直接呈现**，否则月亮高光会被彻底抹平。
 *
 * 因此这里用**对数感知压缩**：把 10⁻³ ~ 10⁵ lux 的照度映射到 0..1，
 * 使"月亮只有太阳的 ~30% 亮度"这种可感知、又不失真的比例得以保留。
 *
 * ## 屏幕空间投影
 * 光源方向先由高度角/方位角做三维投影（东向分量 → 屏幕 x，天顶分量 → 屏幕 y），
 * 再转成 Backdrop 高光 shader 需要的轴向角度：
 * - 日出时太阳在正东 → 高光落在面板**右**缘；
 * - 正午太阳在天顶 → 高光落在面板**上**缘；
 * - 日落时太阳在正西 → 高光落在面板**左**缘。
 *
 * 这是纯屏幕自参考模型（不依赖罗盘/重力传感器），与 iOS/miui 拟物高光一致。
 */
@Immutable
enum class LightKind { SUN, MOON }

/** 一个可见的光源。 */
@Immutable
data class LightSource(
    val kind: LightKind,
    /** 屏幕空间光轴角度（度），语义见 [HighlightStyle.Default.angle]。 */
    val angleDegrees: Float,
    /** 感知强度 0..1（对数压缩后的照度）。 */
    val intensity: Float,
    /** 光源颜色（由色温经 CIE 统一口径换算）。 */
    val color: Color,
    /** 对应色温（K），用于诊断与展示。 */
    val colorTemperatureK: Int,
    /** 真实照度（lux），用于诊断与展示。 */
    val illuminanceLux: Double
)

/**
 * 玻璃光照状态：最多两个光源（太阳 + 月亮），各自独立定位与定强。
 */
@Immutable
data class GlassLightState(
    val sun: LightSource? = null,
    val moon: LightSource? = null
) {
    /** 是否存在任何可见光源；两者皆无时面板不应绘制边缘高光。 */
    val hasLight: Boolean get() = sun != null || moon != null
}

object GlassLighting {

    /** 感知映射的下限：无月夜空的星光量级。 */
    private const val LUX_FLOOR = 1e-3

    /** 感知映射的上限：正午晴空直射日光量级。 */
    private const val LUX_REFERENCE = 1e5

    private val LOG_FLOOR = ln(LUX_FLOOR) / ln(10.0)
    private val LOG_SPAN = (ln(LUX_REFERENCE) / ln(10.0)) - LOG_FLOOR

    /** 太阳/月亮等效的朗伯反射率（用于把光谱功率折算成人眼 lux）。 */
    private const val LUMINOUS_EFFICACY_DIRECT_SUN = 125.0

    /** 大气层外法向辐照度（W/m²），ASTM E490-14 AM0 参考值。 */
    private const val EXTRATERRESTRIAL_IRRADIANCE = 1367.0

    /** 满月在中天时的照度（lux）。 */
    private const val FULL_MOON_ILLUMINANCE_LUX = 0.25

    /**
     * 对数感知压缩：把物理照度映射到 0..1。
     *
     * 1 lux → 0.375；0.25 lux（满月）→ 0.30；1.2×10⁵ lux（正午）→ 1.0。
     */
    fun perceived(illuminanceLux: Double): Float {
        if (illuminanceLux <= 0.0) return 0f
        val log = ln(illuminanceLux) / ln(10.0)
        return ((log - LOG_FLOOR) / LOG_SPAN).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 晴空条件下的太阳**水平面照度**（lux）。
     *
     * 直法向辐照度用 ASHRAE 晴空模型 `DNI = E0 · 0.7^(AM^0.678)`，
     * 再乘 sin(高度角) 得到水平面照度。
     */
    fun sunIlluminanceLux(elevationDegrees: Double): Double {
        if (elevationDegrees <= 0.0) return 0.0
        val airMass = BlackbodyColor.airMass(elevationDegrees)
        val dni = EXTRATERRESTRIAL_IRRADIANCE * 0.7.pow(airMass.pow(0.678))
        return dni * LUMINOUS_EFFICACY_DIRECT_SUN * sin(elevationDegrees * PI / 180.0)
    }

    /**
     * 月光照度（lux）。
     *
     * 满月中天约 0.25 lux，按被照亮比例与高度角线性缩放。
     */
    fun moonIlluminanceLux(elevationDegrees: Double, illuminatedFraction: Double): Double {
        if (elevationDegrees <= 0.0) return 0.0
        val fraction = illuminatedFraction.coerceIn(0.0, 1.0)
        return FULL_MOON_ILLUMINANCE_LUX * fraction *
            sin(elevationDegrees * PI / 180.0)
    }

    /**
     * 光源方向 → 屏幕空间光轴角度（度）。
     *
     * 方位角 0 = 正北、90 = 正东；屏幕 x 轴向东为正，y 轴向上为正。
     */
    fun screenAngleDegrees(elevationDegrees: Double, azimuthDegrees: Double): Float {
        val elev = elevationDegrees * PI / 180.0
        val az = azimuthDegrees * PI / 180.0
        val eastComponent = cos(elev) * sin(az)
        val zenithComponent = sin(elev)
        // 画布 y 轴向下，故取负
        val angle = atan2(-zenithComponent, eastComponent) * 180.0 / PI
        return angle.toFloat()
    }

    /**
     * 由 astro 时刻推算的**无坐标兜底**屏幕角度。
     *
     * 0 = 日出（东/右），0.5 = 天顶（上），1 = 日落（西/左）。
     */
    fun arcAngleDegrees(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val x = cos(PI * p)
        val y = -sin(PI * p)
        return (atan2(y, x) * 180.0 / PI).toFloat()
    }

    /**
     * 构造太阳光源。
     *
     * @return 太阳在地平线以下时返回 null（没有光就没有高光）。
     */
    fun sunSource(elevationDegrees: Double, azimuthDegrees: Double): LightSource? {
        if (elevationDegrees <= 0.0) return null
        val lux = sunIlluminanceLux(elevationDegrees)
        val intensity = perceived(lux)
        if (intensity <= 0f) return null
        val color = BlackbodyColor.sunlight(elevationDegrees)
        return LightSource(
            kind = LightKind.SUN,
            angleDegrees = screenAngleDegrees(elevationDegrees, azimuthDegrees),
            intensity = intensity,
            color = color,
            colorTemperatureK = 0,
            illuminanceLux = lux
        )
    }

    /**
     * 构造月亮光源。
     *
     * @param illuminatedFraction 被照亮比例 0..1
     * @return 月亮在地平线以下或几乎无照亮时返回 null
     */
    fun moonSource(
        elevationDegrees: Double,
        azimuthDegrees: Double,
        illuminatedFraction: Double
    ): LightSource? {
        if (elevationDegrees <= 0.0) return null
        val lux = moonIlluminanceLux(elevationDegrees, illuminatedFraction)
        val intensity = perceived(lux)
        if (intensity <= 0.02f) return null
        return LightSource(
            kind = LightKind.MOON,
            angleDegrees = screenAngleDegrees(elevationDegrees, azimuthDegrees),
            intensity = intensity,
            color = BlackbodyColor.fromColorTemperature(BlackbodyColor.MOON_APPEARANCE_TEMPERATURE_K),
            colorTemperatureK = BlackbodyColor.MOON_APPEARANCE_TEMPERATURE_K.toInt(),
            illuminanceLux = lux
        )
    }
}
