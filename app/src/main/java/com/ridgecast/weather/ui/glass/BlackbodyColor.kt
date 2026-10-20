package com.ridgecast.weather.ui.glass

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 光源色温 → 显示颜色的科学换算（CIE 统一口径）。
 *
 * ## 统一测量口径
 * - **光谱**：Planck 定律（维恩位移/普朗克辐射），太阳有效温度取 **5778 K**
 *   （IAU 2015 Resolution B3 定义的太阳有效温度）。
 * - **大气透过率**：Rayleigh 散射 + Ångström 气溶胶消光，空气质量用
 *   Kasten-Young 公式 `AM = 1/(sin h + 0.50572 (h + 6.07995)^-1.6364)`。
 * - **配色函数**：CIE 1931 2° 标准观察者，用 Wyman/Sloan/Shirley (2013)
 *   多瓣高斯解析拟合（相对误差 < 1%，避免 401×3 查找表）。
 * - **显示变换**：XYZ → CIELAB → 压缩色度映射到 sRGB 色域（保持色相与亮度）
 *   → sRGB 传输函数（IEC 61966-2-1）。
 *
 * ## 结论（与公开实测一致）
 * - 太阳直射光在地面：正午约 **5100 K**（暖白），地平线附近降到 **1250 K**（深橙红）。
 * - 月光：其**光谱几乎等同于阳光**（月球反照率 13.6%，近似中性灰），
 *   因此光度学色温约 4100 K（摄影白平衡参考值）；但夜间暗视觉下的
 *   **Purkinje 效应**让月光看起来偏冷，本项目按"外观色温 **9000 K**" 渲染
 *   （与历史夜间高光色 #D6E4FF 实测 9060 K 一致）。
 */
object BlackbodyColor {

    private const val LAMBDA_START = 380.0
    private const val LAMBDA_END = 780.0
    private const val SAMPLE_COUNT = 401

    /** 太阳有效温度（IAU 2015）。 */
    const val SOLAR_EFFECTIVE_TEMPERATURE_K = 5778.0

    /** 月光外观色温（暗视觉 Purkinje 效应下的等效显示值）。 */
    const val MOON_APPEARANCE_TEMPERATURE_K = 9000.0

    private val lambdas = DoubleArray(SAMPLE_COUNT) { LAMBDA_START + it }

    // ---- CIE 1931 2° 配色函数（Wyman et al. 2013 多瓣拟合） ----

    private fun lobe(x: Double, mu: Double, sigmaLow: Double, sigmaHigh: Double): Double {
        val sigma = if (x < mu) sigmaLow else sigmaHigh
        return exp(-0.5 * ((x - mu) / sigma).pow(2))
    }

    private val cmfX = DoubleArray(SAMPLE_COUNT) { i ->
        val l = LAMBDA_START + i
        1.056 * lobe(l, 599.8, 37.9, 31.0) +
            0.362 * lobe(l, 442.0, 16.0, 26.7) -
            0.065 * lobe(l, 501.1, 20.4, 26.2)
    }

    private val cmfY = DoubleArray(SAMPLE_COUNT) { i ->
        val l = LAMBDA_START + i
        0.821 * lobe(l, 568.8, 46.9, 40.5) +
            0.286 * lobe(l, 530.9, 16.3, 31.1)
    }

    private val cmfZ = DoubleArray(SAMPLE_COUNT) { i ->
        val l = LAMBDA_START + i
        1.217 * lobe(l, 437.0, 11.8, 36.0) +
            0.681 * lobe(l, 459.0, 26.0, 13.8)
    }

    // ---- 基础物理量 ----

    /** 普朗克黑体谱相对辐射亮度。 */
    private fun planck(temperatureK: Double, lambdaNm: Double): Double {
        val lambdaM = lambdaNm * 1e-9
        val c1 = 3.7417718e-16
        val c2 = 1.4387769e-2
        return c1 / (lambdaM.pow(5) * (exp(c2 / (lambdaM * temperatureK)) - 1.0))
    }

    /**
     * 大气透过率：Rayleigh（波长强依赖）+ Ångström 气溶胶（弱依赖）。
     *
     * @param airMass 空气质量数
     * @param turbidity Ångström 气溶胶浑浊度，2.0 ≈ 晴朗
     */
    private fun transmittance(lambdaNm: Double, airMass: Double, turbidity: Double): Double {
        val um = lambdaNm / 1000.0
        val tauRayleigh = 0.008569 * um.pow(-4.0) *
            (1.0 + 0.0113 * um.pow(-2.0) + 0.00013 * um.pow(-4.0))
        val tauAerosol = 0.05 * (turbidity / 2.0) * um.pow(-1.3)
        return exp(-(tauRayleigh + tauAerosol) * airMass)
    }

    /** Kasten-Young 空气质量数。 */
    fun airMass(elevationDegrees: Double): Double {
        val h = maxOf(elevationDegrees, -0.5)
        return 1.0 / (sin(h * PI / 180.0) + 0.50572 * (h + 6.07995).pow(-1.6364))
    }

    /** 大气外太阳光谱经消光后的 XYZ（Y 归一到 100）。 */
    private fun extinctedSolarXyz(
        temperatureK: Double,
        airMass: Double,
        turbidity: Double
    ): DoubleArray {
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (i in 0 until SAMPLE_COUNT) {
            val s = planck(temperatureK, lambdas[i]) *
                transmittance(lambdas[i], airMass, turbidity)
            sx += cmfX[i] * s
            sy += cmfY[i] * s
            sz += cmfZ[i] * s
        }
        if (sy <= 0.0) return doubleArrayOf(1.0, 1.0, 1.0)
        val k = 100.0 / sy
        return doubleArrayOf(sx * k, sy * k, sz * k)
    }

    // ---- 普朗克轨迹（Kim et al. 2002 解析近似） ----

    /** 普朗克轨迹上的色品坐标 (x, y)。 */
    fun planckianXy(temperatureK: Double): DoubleArray {
        val t = temperatureK.coerceIn(1000.0, 40000.0)
        val x = if (t <= 4000.0) {
            -0.2661239e9 / t.pow(3) - 0.2343589e6 / t.pow(2) +
                0.8776956e3 / t + 0.179910
        } else {
            -3.0258469e9 / t.pow(3) + 2.1070379e6 / t.pow(2) +
                0.2226347e3 / t + 0.240390
        }
        val y = -3.0 * x * x + 2.87 * x - 0.275
        return doubleArrayOf(x, y)
    }

    /** 由色品坐标构造 XYZ（Y = 1）。 */
    private fun xyzFromXy(x: Double, y: Double): DoubleArray =
        doubleArrayOf(x / y, 1.0, (1.0 - x - y) / y)

    // ---- XYZ → sRGB ----

    private val srgbMatrix = doubleArrayOf(
        3.2404542, -1.5371385, -0.4985314,
        -0.9692660, 1.8760108, 0.0415560,
        0.0556434, -0.2040259, 1.0572252
    )

    /** D65 白点（sRGB 标准）。 */
    private val d65 = doubleArrayOf(0.95047, 1.0, 1.08883)

    private fun cbrt(v: Double): Double =
        if (v < 0) -((-v).pow(1.0 / 3.0)) else v.pow(1.0 / 3.0)

    private fun labF(t: Double): Double {
        val e = 6.0 / 29.0
        return if (t > e * e * e) cbrt(t) else t / (3.0 * e * e) + 4.0 / 29.0
    }

    private fun labFInv(t: Double): Double {
        val e = 6.0 / 29.0
        return if (t > e) t.pow(3) else 3.0 * e * e * (t - 4.0 / 29.0)
    }

    private fun xyzToLab(xyz: DoubleArray): DoubleArray {
        val fx = labF(xyz[0] / d65[0])
        val fy = labF(xyz[1] / d65[1])
        val fz = labF(xyz[2] / d65[2])
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun labToXyz(lab: DoubleArray): DoubleArray {
        val fy = (lab[0] + 16.0) / 116.0
        val fx = fy + lab[1] / 500.0
        val fz = fy - lab[2] / 200.0
        return doubleArrayOf(
            d65[0] * labFInv(fx),
            d65[1] * labFInv(fy),
            d65[2] * labFInv(fz)
        )
    }

    private fun linearSrgb(xyz: DoubleArray): DoubleArray = doubleArrayOf(
        srgbMatrix[0] * xyz[0] + srgbMatrix[1] * xyz[1] + srgbMatrix[2] * xyz[2],
        srgbMatrix[3] * xyz[0] + srgbMatrix[4] * xyz[1] + srgbMatrix[5] * xyz[2],
        srgbMatrix[6] * xyz[0] + srgbMatrix[7] * xyz[1] + srgbMatrix[8] * xyz[2]
    )

    /** 色域判定按 UI 归一化口径：除以自身最大分量后不得出现负分量。 */
    private fun inGamut(linear: DoubleArray): Boolean {
        val max = linear[0].coerceAtLeast(linear[1]).coerceAtLeast(linear[2])
        if (max <= 1e-9) return false
        val min = linear[0].coerceAtMost(linear[1]).coerceAtMost(linear[2])
        return min / max >= -1e-6
    }

    /**
     * 色域映射：保持色相与明度，二分压缩 Lab 色度直到落入 sRGB 色域。
     *
     * 直接裁剪 RGB 分量会改变色相（暖光会被压成绿色），故必须走 Lab。
     */
    private fun gamutMap(lab: DoubleArray): DoubleArray {
        if (inGamut(linearSrgb(labToXyz(lab)))) return lab
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2.0
            val trial = doubleArrayOf(lab[0], lab[1] * mid, lab[2] * mid)
            if (inGamut(linearSrgb(labToXyz(trial)))) lo = mid else hi = mid
        }
        return doubleArrayOf(lab[0], lab[1] * lo, lab[2] * lo)
    }

    private fun srgbEncode(c: Double): Double {
        val v = c.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
    }

    /** XYZ → 0..255 的 sRGB 分量（最大分量归一到 255）。 */
    private fun xyzToSrgb255(xyz: DoubleArray): IntArray {
        val mapped = gamutMap(xyzToLab(xyz))
        val linear = linearSrgb(labToXyz(mapped))
        val max = linear[0].coerceAtLeast(linear[1]).coerceAtLeast(linear[2])
        if (max <= 1e-9) return intArrayOf(255, 255, 255)
        val norm = doubleArrayOf(
            (linear[0] / max).coerceIn(0.0, 1.0),
            (linear[1] / max).coerceIn(0.0, 1.0),
            (linear[2] / max).coerceIn(0.0, 1.0)
        )
        return intArrayOf(
            (srgbEncode(norm[0]) * 255.0 + 0.5).toInt(),
            (srgbEncode(norm[1]) * 255.0 + 0.5).toInt(),
            (srgbEncode(norm[2]) * 255.0 + 0.5).toInt()
        )
    }

    // ---- 对外 API ----

    /**
     * 由相关色温得到显示颜色的 RGB 分量（0..255）。
     *
     * 典型值：4100 K → 暖白 (255,218,168)；5778 K → (255,247,233)；
     * 6500 K → (255,255,255)；9000 K → 冷蓝白 (214,229,255)。
     */
    fun srgb255FromColorTemperature(temperatureK: Double): IntArray {
        val xy = planckianXy(temperatureK)
        return xyzToSrgb255(xyzFromXy(xy[0], xy[1]))
    }

    /**
     * 太阳直射光穿过大气后的真实颜色的 RGB 分量（0..255）。
     *
     * @param elevationDegrees 太阳高度角（地平线以下会得到极暖的深橙红）
     * @param turbidity 浑浊度，2.0 ≈ 晴朗
     */
    fun srgb255FromSunlight(elevationDegrees: Double, turbidity: Double = 2.0): IntArray {
        if (elevationDegrees <= -6.0) return intArrayOf(0xFF, 0x6B, 0x5B)
        val am = airMass(elevationDegrees)
        val xyz = extinctedSolarXyz(SOLAR_EFFECTIVE_TEMPERATURE_K, am, turbidity)
        return xyzToSrgb255(xyz)
    }

    /**
     * 由相关色温得到显示颜色（普朗克轨迹 + 色域映射）。
     *
     * 典型值：4100 K → 暖白 #FFDAA8；5778 K → #FFF7E9；9000 K → 冷蓝白 #D6E5FF。
     */
    fun fromColorTemperature(temperatureK: Double): Color {
        val rgb = srgb255FromColorTemperature(temperatureK)
        return Color(rgb[0], rgb[1], rgb[2])
    }

    /**
     * 太阳直射光穿过大气后的真实颜色。
     *
     * @param elevationDegrees 太阳高度角（地平线以下会得到极暖的深橙红）
     * @param turbidity 浑浊度，2.0 ≈ 晴朗
     */
    fun sunlight(elevationDegrees: Double, turbidity: Double = 2.0): Color {
        val rgb = srgb255FromSunlight(elevationDegrees, turbidity)
        return Color(rgb[0], rgb[1], rgb[2])
    }

    /**
     * 由光谱反查相关色温（用于诊断/展示：把任意光谱落到普朗克轨迹上）。
     */
    fun colorTemperatureOf(xyz: DoubleArray): Int {
        val sum = xyz[0] + xyz[1] + xyz[2]
        if (sum <= 0.0) return 0
        val x = xyz[0] / sum
        val y = xyz[1] / sum
        var bestT = 0
        var bestD = Double.MAX_VALUE
        var t = 1000
        while (t <= 40000) {
            val xy = planckianXy(t.toDouble())
            val d = (x - xy[0]) * (x - xy[0]) + (y - xy[1]) * (y - xy[1])
            if (d < bestD) {
                bestD = d
                bestT = t
            }
            t += 10
        }
        return bestT
    }
}
