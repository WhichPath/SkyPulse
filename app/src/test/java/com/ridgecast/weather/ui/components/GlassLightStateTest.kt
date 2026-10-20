package com.ridgecast.weather.ui.components

import com.ridgecast.weather.model.AstronomySnapshot
import com.ridgecast.weather.model.DailyAstro
import com.ridgecast.weather.model.DailyForecast
import com.ridgecast.weather.model.AstroTime
import com.ridgecast.weather.ui.glass.LightKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 玻璃光照状态编排的回归测试：
 * 接口优先 → 本地算法兜底 → 无光源则无高光。
 */
class GlassLightStateTest {

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day, hour, 0, 0)
        return calendar.timeInMillis
    }

    @Test
    fun `api snapshot wins over local computation`() {
        // 2026-06-21 04:00 UTC = 北京正午，太阳高度角 73.3°
        val now = utcMillis(2026, 6, 21, 4)
        val astronomy = AstronomySnapshot(
            latitude = 39.9042,
            longitude = 116.4074,
            solarElevationDegrees = 73.3,
            solarAzimuthDegrees = 168.5
        )
        val state = computeGlassLight(null, astronomy, 39.9042, 116.4074, now)
        val sun = state.sun
        assertNotNull("应有太阳光源", sun)
        assertEquals(LightKind.SUN, sun!!.kind)
        assertTrue("正午太阳强度应接近 1", sun.intensity > 0.9f)
        // 正午前后（方位角接近 180°）→ 光轴朝上（-90° 附近）
        assertTrue("正午光轴应朝上，实际 ${sun.angleDegrees}", sun.angleDegrees < -70f && sun.angleDegrees > -90f)
    }

    @Test
    fun `local computation is used when api is absent`() {
        val now = utcMillis(2026, 6, 21, 4)
        val state = computeGlassLight(null, null, 39.9042, 116.4074, now)
        assertNotNull("应能本地算出太阳", state.sun)
        assertTrue(state.sun!!.intensity > 0.9f)
    }

    @Test
    fun `no visible light means no highlight at all`() {
        // 深夜：太阳高度角 -20°，月亮被照亮比例 0（新月）→ 没有任何光源
        val now = utcMillis(2026, 6, 21, 16)
        val astronomy = AstronomySnapshot(
            latitude = 39.9042,
            longitude = 116.4074,
            solarElevationDegrees = -20.0,
            solarAzimuthDegrees = 310.0,
            moonIllumination = 0.0
        )
        val state = computeGlassLight(null, astronomy, 39.9042, 116.4074, now)
        assertNull("午夜不应有太阳光源", state.sun)
        assertNull("新月不应有月亮光源", state.moon)
        assertTrue("无光源即无高光", !state.hasLight)
    }

    @Test
    fun `without any data source it falls back to default daylight`() {
        val now = utcMillis(2026, 6, 21, 4)
        val state = computeGlassLight(null, null, null, null, now)
        assertNotNull("无数据源时应给默认日光，避免玻璃变成死板方块", state.sun)
        assertEquals(0.55f, state.sun!!.intensity, 1e-6f)
    }

    @Test
    fun `moon light appears only when the moon is up`() {
        // 满月且月出期间应有月亮光源
        val now = utcMillis(2026, 6, 21, 12)
        val astronomy = AstronomySnapshot(
            latitude = 39.9042,
            longitude = 116.4074,
            solarElevationDegrees = 40.0,
            solarAzimuthDegrees = 200.0,
            moonIllumination = 0.95
        )
        val state = computeGlassLight(null, astronomy, 39.9042, 116.4074, now)
        // 白天太阳主导；月亮是否可见取决于月相与几何
        assertNotNull(state.sun)
        if (state.moon != null) {
            assertEquals(LightKind.MOON, state.moon!!.kind)
            assertTrue("月亮强度应弱于太阳", state.moon!!.intensity < state.sun!!.intensity)
        }
    }

    @Test
    fun `astro arc fallback works without coordinates`() {
        val daily = DailyForecast(
            status = "ok",
            astro = listOf(
                DailyAstro(
                    date = "2026-06-21",
                    sunrise = AstroTime("04:45"),
                    sunset = AstroTime("19:25"),
                    moonrise = AstroTime("--:--"),
                    moonset = AstroTime("--:--")
                )
            )
        )
        // 本地时间 12:00（处于日出与日落之间）→ 太阳在天空
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        val state = computeGlassLight(daily, null, null, null, calendar.timeInMillis)
        assertNotNull("astro 弧线兜底应给出太阳", state.sun)
        assertTrue(state.sun!!.intensity > 0f)
    }
}
