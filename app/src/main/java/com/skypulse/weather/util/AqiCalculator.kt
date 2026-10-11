package com.skypulse.weather.util

import kotlin.math.roundToInt

/**
 * 美国 EPA (Environmental Protection Agency) 空气质量指数 (AQI) 计算器。
 *
 * 算法依据: EPA Technical Assistance Document for Reporting of Daily Air Quality (40 CFR Part 58)。
 * 使用分段线性插值公式:
 *   Ip = ((I_high - I_low) / (BP_high - BP_low)) * (Cp - BP_low) + I_low
 *
 * 最终 AQI 为各监测污染物分指数中的最大值，对应污染物即为首要污染物。
 */
object AqiCalculator {

    data class AqiResult(
        val aqi: Int,
        val category: String,
        val primaryPollutant: String?,
        val individualAqi: Map<String, Int> = emptyMap()
    )

    private data class Breakpoint(
        val cLow: Double,
        val cHigh: Double,
        val iLow: Int,
        val iHigh: Int
    )

    // PM2.5 浓度分界点 (μg/m³，24小时平均，EPA 最新标准)
    private val PM25_BREAKPOINTS = listOf(
        Breakpoint(0.0, 9.0, 0, 50),
        Breakpoint(9.1, 35.4, 51, 100),
        Breakpoint(35.5, 55.4, 101, 150),
        Breakpoint(55.5, 125.4, 151, 200),
        Breakpoint(125.5, 225.4, 201, 300),
        Breakpoint(225.5, 325.4, 301, 400),
        Breakpoint(325.5, 500.4, 401, 500)
    )

    // PM10 浓度分界点 (μg/m³，24小时平均)
    private val PM10_BREAKPOINTS = listOf(
        Breakpoint(0.0, 54.0, 0, 50),
        Breakpoint(55.0, 154.0, 51, 100),
        Breakpoint(155.0, 254.0, 101, 150),
        Breakpoint(255.0, 354.0, 151, 200),
        Breakpoint(355.0, 424.0, 201, 300),
        Breakpoint(425.0, 504.0, 301, 400),
        Breakpoint(505.0, 604.0, 401, 500)
    )

    // O3 浓度分界点 (ppb，8小时 / 1小时)
    private val O3_BREAKPOINTS = listOf(
        Breakpoint(0.0, 54.0, 0, 50),
        Breakpoint(55.0, 70.0, 51, 100),
        Breakpoint(71.0, 85.0, 101, 150),
        Breakpoint(86.0, 105.0, 151, 200),
        Breakpoint(106.0, 200.0, 201, 300),
        Breakpoint(201.0, 504.0, 301, 400),
        Breakpoint(505.0, 604.0, 401, 500)
    )

    // CO 浓度分界点 (ppm)
    private val CO_BREAKPOINTS = listOf(
        Breakpoint(0.0, 4.4, 0, 50),
        Breakpoint(4.5, 9.4, 51, 100),
        Breakpoint(9.5, 12.4, 101, 150),
        Breakpoint(12.5, 15.4, 151, 200),
        Breakpoint(15.5, 30.4, 201, 300),
        Breakpoint(30.5, 40.4, 301, 400),
        Breakpoint(40.5, 50.4, 401, 500)
    )

    // SO2 浓度分界点 (ppb)
    private val SO2_BREAKPOINTS = listOf(
        Breakpoint(0.0, 35.0, 0, 50),
        Breakpoint(36.0, 75.0, 51, 100),
        Breakpoint(76.0, 185.0, 101, 150),
        Breakpoint(186.0, 304.0, 151, 200),
        Breakpoint(305.0, 604.0, 201, 300),
        Breakpoint(605.0, 804.0, 301, 400),
        Breakpoint(805.0, 1004.0, 401, 500)
    )

    // NO2 浓度分界点 (ppb)
    private val NO2_BREAKPOINTS = listOf(
        Breakpoint(0.0, 53.0, 0, 50),
        Breakpoint(54.0, 100.0, 51, 100),
        Breakpoint(101.0, 360.0, 101, 150),
        Breakpoint(361.0, 649.0, 151, 200),
        Breakpoint(650.0, 1249.0, 201, 300),
        Breakpoint(1250.0, 1649.0, 301, 400),
        Breakpoint(1650.0, 2049.0, 401, 500)
    )

    /**
     * 根据基础污染物浓度计算美国 AQI。
     *
     * @param pm25 PM2.5 浓度 (μg/m³)
     * @param pm10 PM10 浓度 (μg/m³)
     * @param o3 O3 浓度 (若单位为 μg/m³ 则内部按 1 ppb ≈ 1.96 μg/m³ 折算为 ppb)
     * @param no2 NO2 浓度 (若单位为 μg/m³ 则内部按 1 ppb ≈ 1.88 μg/m³ 折算为 ppb)
     * @param so2 SO2 浓度 (若单位为 μg/m³ 则内部按 1 ppb ≈ 2.62 μg/m³ 折算为 ppb)
     * @param co CO 浓度 (若单位为 mg/m³ 则内部按 1 ppm ≈ 1.15 mg/m³ 折算为 ppm)
     */
    fun calculate(
        pm25: Double? = null,
        pm10: Double? = null,
        o3: Double? = null,
        no2: Double? = null,
        so2: Double? = null,
        co: Double? = null
    ): AqiResult? {
        val subIndices = mutableListOf<Pair<String, Int>>()

        pm25?.let {
            val truncated = kotlin.math.floor(it * 10) / 10.0
            computeSubIndex(truncated, PM25_BREAKPOINTS)?.let { aqi ->
                subIndices.add("PM2.5" to aqi)
            }
        }

        pm10?.let {
            val truncated = kotlin.math.floor(it)
            computeSubIndex(truncated, PM10_BREAKPOINTS)?.let { aqi ->
                subIndices.add("PM10" to aqi)
            }
        }

        o3?.let {
            // 若数值大于 1.0 且小于等于 500，通常为 μg/m³，转为 ppb；若小于 1.0 通常为 ppm
            val ppb = when {
                it > 1.0 -> kotlin.math.floor(it / 1.963)
                it > 0.0 -> kotlin.math.floor(it * 1000)
                else -> 0.0
            }
            computeSubIndex(ppb, O3_BREAKPOINTS)?.let { aqi ->
                subIndices.add("O₃" to aqi)
            }
        }

        co?.let {
            // 若数值大于 0 且小于 50，若单位为 mg/m³ 则转为 ppm: mg/m³ * 24.45 / 28.01
            val ppm = kotlin.math.floor((it * 0.873) * 10) / 10.0
            computeSubIndex(ppm, CO_BREAKPOINTS)?.let { aqi ->
                subIndices.add("CO" to aqi)
            }
        }

        so2?.let {
            val ppb = kotlin.math.floor(it * 0.382)
            computeSubIndex(ppb, SO2_BREAKPOINTS)?.let { aqi ->
                subIndices.add("SO₂" to aqi)
            }
        }

        no2?.let {
            val ppb = kotlin.math.floor(it * 0.531)
            computeSubIndex(ppb, NO2_BREAKPOINTS)?.let { aqi ->
                subIndices.add("NO₂" to aqi)
            }
        }

        if (subIndices.isEmpty()) return null

        val maxEntry = subIndices.maxByOrNull { it.second } ?: return null
        val maxAqi = maxEntry.second
        val primary = if (maxAqi > 50) maxEntry.first else null

        return AqiResult(
            aqi = maxAqi,
            category = getCategory(maxAqi),
            primaryPollutant = primary,
            individualAqi = subIndices.toMap()
        )
    }

    fun calculatePm25Aqi(pm25: Double?): Int? {
        if (pm25 == null) return null
        val truncated = kotlin.math.floor(pm25 * 10) / 10.0
        return computeSubIndex(truncated, PM25_BREAKPOINTS)
    }

    fun calculatePm10Aqi(pm10: Double?): Int? {
        if (pm10 == null) return null
        val truncated = kotlin.math.floor(pm10)
        return computeSubIndex(truncated, PM10_BREAKPOINTS)
    }

    fun calculateO3Aqi(o3: Double?): Int? {
        if (o3 == null) return null
        val ppb = when {
            o3 > 1.0 -> kotlin.math.floor(o3 / 1.963)
            o3 > 0.0 -> kotlin.math.floor(o3 * 1000)
            else -> 0.0
        }
        return computeSubIndex(ppb, O3_BREAKPOINTS)
    }

    fun calculateCoAqi(co: Double?): Int? {
        if (co == null) return null
        val ppm = kotlin.math.floor((co * 0.873) * 10) / 10.0
        return computeSubIndex(ppm, CO_BREAKPOINTS)
    }

    fun calculateSo2Aqi(so2: Double?): Int? {
        if (so2 == null) return null
        val ppb = kotlin.math.floor(so2 * 0.382)
        return computeSubIndex(ppb, SO2_BREAKPOINTS)
    }

    fun calculateNo2Aqi(no2: Double?): Int? {
        if (no2 == null) return null
        val ppb = kotlin.math.floor(no2 * 0.531)
        return computeSubIndex(ppb, NO2_BREAKPOINTS)
    }

    fun getCategory(aqi: Int): String = when {
        aqi <= 50 -> "优"
        aqi <= 100 -> "中等"
        aqi <= 150 -> "轻度不健康"
        aqi <= 200 -> "不健康"
        aqi <= 300 -> "重度不健康"
        else -> "危险"
    }

    private fun computeSubIndex(concentration: Double, breakpoints: List<Breakpoint>): Int? {
        if (concentration < 0.0) return null
        val bp = breakpoints.firstOrNull { concentration in it.cLow..it.cHigh }
            ?: if (concentration > breakpoints.last().cHigh) breakpoints.last() else null
            ?: return null

        val aqiDouble = ((bp.iHigh - bp.iLow).toDouble() / (bp.cHigh - bp.cLow)) * (concentration - bp.cLow) + bp.iLow
        return aqiDouble.roundToInt().coerceIn(0, 500)
    }
}
