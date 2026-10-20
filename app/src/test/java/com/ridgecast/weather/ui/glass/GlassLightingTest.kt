package com.ridgecast.weather.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 玻璃光照模型的物理正确性回归测试。
 *
 * 覆盖：
 * - CIE 1931 色品换算（色温 → sRGB）；
 * - 太阳光谱经大气消光后的色温/颜色随高度角变化；
 * - NOAA 太阳位置算法（对照公开天文数据）；
 * - 对数感知压缩的照度 → 强度映射；
 * - 屏幕空间光轴投影（日出在右、正午在上、日落在左）。
 */
class GlassLightingTest {

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0
    ): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, 0)
        return calendar.timeInMillis
    }

    // ---------------- 色温换算 ----------------

    @Test
    fun `6500K is neutral white`() {
        val rgb = BlackbodyColor.srgb255FromColorTemperature(6500.0)
        assertEquals(255, rgb[0])
        assertEquals(255, rgb[1])
        assertTrue("蓝分量应接近 255", rgb[2] >= 250)
    }

    @Test
    fun `4100K moonlight illuminant is warm white`() {
        val rgb = BlackbodyColor.srgb255FromColorTemperature(4100.0)
        assertTrue("R>G", rgb[0] > rgb[1])
        assertTrue("G>B", rgb[1] > rgb[2])
    }

    @Test
    fun `9000K night appearance is cool blue white`() {
        val rgb = BlackbodyColor.srgb255FromColorTemperature(9000.0)
        assertTrue("B>R", rgb[2] > rgb[0])
        assertTrue("B>G", rgb[2] > rgb[1])
    }

    @Test
    fun `sunlight at zenith is warm white and at horizon is deep orange`() {
        val noon = BlackbodyColor.srgb255FromSunlight(90.0)
        val horizon = BlackbodyColor.srgb255FromSunlight(0.0)
        assertTrue("正午应偏暖 (R>=B)", noon[0] >= noon[2])
        assertTrue("地平线应强烈偏橙 (R 远大于 B)", horizon[0] - horizon[2] > 120)
    }

    @Test
    fun `sunlight gets warmer as elevation drops`() {
        val high = BlackbodyColor.srgb255FromSunlight(60.0)
        val low = BlackbodyColor.srgb255FromSunlight(3.0)
        // 越低越红：蓝分量应显著下降
        assertTrue("低空太阳应更红", low[2] < high[2] - 40)
    }

    // ---------------- 太阳位置 ----------------

    @Test
    fun `beijing summer solstice noon elevation matches astronomical value`() {
        // 2026-06-21 04:00 UTC = 北京 12:00（真太阳时略偏，理论值约 73.3°）
        val position = SkyPosition.sun(
            utcMillis(2026, 6, 21, 4),
            latitude = 39.9042,
            longitude = 116.4074
        )
        assertEquals(73.3, position.elevationDegrees, 0.6)
        assertEquals(168.5, position.azimuthDegrees, 1.5)
    }

    @Test
    fun `beijing midnight sun is below horizon`() {
        val position = SkyPosition.sun(
            utcMillis(2026, 6, 21, 16),
            latitude = 39.9042,
            longitude = 116.4074
        )
        assertTrue("午夜太阳应在地平线以下", position.elevationDegrees < 0)
    }

    @Test
    fun `equinox sunrise is due east and sunset due west`() {
        val rise = SkyPosition.sunriseSunsetAzimuth(
            utcMillis(2026, 3, 20, 12),
            latitude = 39.9042,
            longitude = 116.4074
        )
        assertTrue("春分日出应接近正东", rise != null && Math.abs(rise.first - 90.0) < 3.0)
        assertTrue("春分日落应接近正西", rise != null && Math.abs(rise.second - 270.0) < 3.0)
    }

    // ---------------- 强度映射 ----------------

    @Test
    fun `perceived intensity compresses five orders of magnitude`() {
        assertEquals(1f, GlassLighting.perceived(1.2e5), 0.02f)
        assertEquals(0f, GlassLighting.perceived(0.0), 0.001f)
        // 满月中天 0.25 lux ≈ 太阳的 30%
        val moon = GlassLighting.perceived(0.25)
        assertTrue("满月感知强度应在 0.25~0.35", moon in 0.25f..0.35f)
    }

    @Test
    fun `sun illuminance vanishes below horizon`() {
        assertEquals(0.0, GlassLighting.sunIlluminanceLux(-1.0), 0.0001)
        assertTrue("正午照度应在 10 万 lux 量级", GlassLighting.sunIlluminanceLux(90.0) > 1.0e5)
    }

    @Test
    fun `moon illuminance scales with illuminated fraction`() {
        val full = GlassLighting.moonIlluminanceLux(90.0, 1.0)
        val half = GlassLighting.moonIlluminanceLux(90.0, 0.5)
        assertEquals(full / 2.0, half, 1e-9)
        assertEquals(0.0, GlassLighting.moonIlluminanceLux(-5.0, 1.0), 1e-9)
    }

    // ---------------- 屏幕投影 ----------------

    @Test
    fun `screen angle maps sunrise to right, noon to top, sunset to left`() {
        val sunrise = GlassLighting.screenAngleDegrees(0.0, 58.0)
        val noon = GlassLighting.screenAngleDegrees(73.0, 180.0)
        val sunset = GlassLighting.screenAngleDegrees(0.0, 302.0)
        assertEquals("日出应在右缘", 0f, sunrise, 0.5f)
        assertEquals("正午应在上缘", -90f, noon, 0.5f)
        assertTrue("日落应在左缘", Math.abs(sunset - 180f) < 0.5f || Math.abs(sunset + 180f) < 0.5f)
    }

    @Test
    fun `no light source below horizon`() {
        assertNull(GlassLighting.sunSource(-1.0, 180.0))
        assertNull(GlassLighting.moonSource(-1.0, 0.0, 1.0))
    }

    // ---------------- 月相 ----------------

    @Test
    fun `moon phase names map to illuminated fraction`() {
        assertEquals(0.0, MoonIllumination.fromPhaseName("新月")!!, 1e-9)
        assertEquals(1.0, MoonIllumination.fromPhaseName("满月")!!, 1e-9)
        assertEquals(0.5, MoonIllumination.fromPhaseName("上弦月")!!, 1e-9)
        assertEquals(0.25, MoonIllumination.fromPhaseName("峨眉月")!!, 1e-9)
        assertEquals(0.75, MoonIllumination.fromPhaseName("盈凸月")!!, 1e-9)
        assertNull(MoonIllumination.fromPhaseName("未知月相"))
        assertNull(MoonIllumination.fromPhaseName(null))
    }
}
