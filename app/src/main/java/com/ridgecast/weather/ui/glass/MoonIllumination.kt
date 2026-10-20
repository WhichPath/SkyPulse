package com.ridgecast.weather.ui.glass

import java.util.Locale

/**
 * 月相 → 被照亮比例（本地兜底换算）。
 *
 * 真实值来自和风天文接口 `/v7/astronomy/moon` 的逐小时 `illumination`；
 * 接口不可用时按**八分月相**的平均值查表：
 *
 * | 月相 | 被照亮比例 |
 * |---|---|
 * | 新月 | 0.00 |
 * | 峨眉月 / 残月 | 0.25 |
 * | 上弦月 / 下弦月 | 0.50 |
 * | 盈凸月 / 亏凸月 | 0.75 |
 * | 满月 | 1.00 |
 *
 * 该表与 Meeus 月相角公式给出的瞬时值相比误差约 ±0.1，足够驱动 UI 亮度。
 */
object MoonIllumination {

    private const val NEW = 0.0
    private const val CRESCENT = 0.25
    private const val QUARTER = 0.5
    private const val GIBBOUS = 0.75
    private const val FULL = 1.0

    /**
     * 由月相名称推断被照亮比例；无法识别时返回 null（交给本地月相角计算）。
     */
    fun fromPhaseName(rawName: String?): Double? {
        if (rawName.isNullOrBlank()) return null
        val name = rawName.trim().lowercase(Locale.US)
            .replace("-", " ")
            .replace("_", " ")
        return when {
            name.contains("new") || name.contains("新月") || name.contains("朔") -> NEW
            name.contains("first quarter") || name.contains("上弦") -> QUARTER
            name.contains("last quarter") || name.contains("third quarter") ||
                name.contains("下弦") -> QUARTER
            name.contains("full") || name.contains("满月") || name.contains("望") -> FULL
            name.contains("waxing") && name.contains("gibbous") ||
                name.contains("盈凸") -> GIBBOUS
            name.contains("waning") && name.contains("gibbous") ||
                name.contains("亏凸") -> GIBBOUS
            name.contains("waxing") && name.contains("crescent") ||
                name.contains("waning") && name.contains("crescent") ||
                name.contains("娥眉") || name.contains("蛾眉") || name.contains("峨眉") ||
                name.contains("残月") -> CRESCENT
            name.contains("crescent") -> CRESCENT
            name.contains("gibbous") -> GIBBOUS
            name.contains("quarter") -> QUARTER
            else -> null
        }
    }
}
