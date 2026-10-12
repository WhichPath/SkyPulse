package com.skypulse.weather.util

import kotlin.math.exp
import kotlin.math.max

/**
 * 香港环境保护署 (EPD) 空气质素健康指数 (AQHI) 计算器。
 *
 * 官方算法:
 *   总健康风险增幅 %AR = %AR(NO2) + %AR(SO2) + %AR(O3) + max(%AR(PM2.5), %AR(PM10))
 *   各污染物 %AR = [exp(β * C) - 1] * 100%
 *   其中浓度 C 官方基准单位为 μg/m³
 *
 * 官方回归系数 β:
 *   NO2:  0.0004462559
 *   SO2:  0.0001393235
 *   O3:   0.0004888034
 *   PM10: 0.0002821751
 *   PM2.5:0.0002180567
 *
 * 分级标准 (1-10 及 10+):
 *   低 (1-3):    %AR <= 5.64%
 *     1: %AR <= 1.88%
 *     2: 1.88% < %AR <= 3.76%
 *     3: 3.76% < %AR <= 5.64%
 *   中 (4-6):    5.64% < %AR <= 11.29%
 *     4: 5.64% < %AR <= 7.52%
 *     5: 7.52% < %AR <= 9.41%
 *     6: 9.41% < %AR <= 11.29%
 *   高 (7):      11.29% < %AR <= 12.91%
 *   甚高 (8-10): 12.91% < %AR <= 19.37%
 *     8: 12.91% < %AR <= 15.06%
 *     9: 15.06% < %AR <= 17.22%
 *     10: 17.22% < %AR <= 19.37%
 *   严重 (10+):  %AR > 19.37%
 */
object AqhiCalculator {

    data class AqhiResult(
        val index: Int,            // 1..10, 11 代表 10+
        val displayValue: String,  // "1".."10", "10+"
        val category: String,      // "低", "中", "高", "甚高", "严重"
        val addedRisk: Double      // %AR 风险增幅数值
    )

    private const val BETA_NO2 = 0.0004462559
    private const val BETA_SO2 = 0.0001393235
    private const val BETA_O3 = 0.0004888034
    private const val BETA_PM10 = 0.0002821751
    private const val BETA_PM25 = 0.0002180567

    /**
     * 根据基础污染物浓度计算香港 AQHI。
     *
     * @param pm25 PM2.5 浓度 (μg/m³)
     * @param pm10 PM10 浓度 (μg/m³)
     * @param o3 O3 浓度 (若单位为 ppb 则乘以 1.963 折算为 μg/m³；若已是 μg/m³ 则直接使用)
     * @param no2 NO2 浓度 (若单位为 ppb 则乘以 1.88 折算为 μg/m³；若已是 μg/m³ 则直接使用)
     * @param so2 SO2 浓度 (若单位为 ppb 则乘以 2.62 折算为 μg/m³；若已是 μg/m³ 则直接使用)
     */
    fun calculate(
        pm25: Double? = null,
        pm10: Double? = null,
        o3: Double? = null,
        no2: Double? = null,
        so2: Double? = null,
        o3Unit: String? = null,
        no2Unit: String? = null,
        so2Unit: String? = null
    ): AqhiResult? {
        if (pm25 == null && pm10 == null && o3 == null && no2 == null && so2 == null) {
            return null
        }

        val cPm25 = pm25?.coerceAtLeast(0.0) ?: 0.0
        val cPm10 = pm10?.coerceAtLeast(0.0) ?: 0.0

        // 统一气体浓度折算为 μg/m³：若为 μg/m³ 则直接使用，避免二次乘算
        val cO3 = o3?.let {
            if (it <= 0) 0.0
            else if (isMicrogramsPerCubicMeter(o3Unit)) it
            else it * 1.963
        } ?: 0.0

        val cNo2 = no2?.let {
            if (it <= 0) 0.0
            else if (isMicrogramsPerCubicMeter(no2Unit)) it
            else it * 1.88
        } ?: 0.0

        val cSo2 = so2?.let {
            if (it <= 0) 0.0
            else if (isMicrogramsPerCubicMeter(so2Unit)) it
            else it * 2.62
        } ?: 0.0

        val arPm25 = if (pm25 != null) (exp(BETA_PM25 * cPm25) - 1.0) * 100.0 else 0.0
        val arPm10 = if (pm10 != null) (exp(BETA_PM10 * cPm10) - 1.0) * 100.0 else 0.0
        val arPm = max(arPm25, arPm10)

        val arO3 = if (o3 != null) (exp(BETA_O3 * cO3) - 1.0) * 100.0 else 0.0
        val arNo2 = if (no2 != null) (exp(BETA_NO2 * cNo2) - 1.0) * 100.0 else 0.0
        val arSo2 = if (so2 != null) (exp(BETA_SO2 * cSo2) - 1.0) * 100.0 else 0.0

        val totalAr = arNo2 + arSo2 + arO3 + arPm

        val (index, display, category) = when {
            totalAr <= 1.88 -> Triple(1, "1", "低")
            totalAr <= 3.76 -> Triple(2, "2", "低")
            totalAr <= 5.64 -> Triple(3, "3", "低")
            totalAr <= 7.52 -> Triple(4, "4", "中")
            totalAr <= 9.41 -> Triple(5, "5", "中")
            totalAr <= 11.29 -> Triple(6, "6", "中")
            totalAr <= 12.91 -> Triple(7, "7", "高")
            totalAr <= 15.06 -> Triple(8, "8", "甚高")
            totalAr <= 17.22 -> Triple(9, "9", "甚高")
            totalAr <= 19.37 -> Triple(10, "10", "甚高")
            else -> Triple(11, "10+", "严重")
        }

        return AqhiResult(
            index = index,
            displayValue = display,
            category = category,
            addedRisk = totalAr
        )
    }

    private fun isMicrogramsPerCubicMeter(unit: String?): Boolean {
        if (unit.isNullOrBlank()) return false
        val u = unit.trim().lowercase()
        return u == "μg/m3" || u == "ug/m3" || u == "μg/m³"
    }
}
