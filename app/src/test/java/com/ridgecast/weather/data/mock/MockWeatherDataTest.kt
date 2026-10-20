package com.ridgecast.weather.data.mock

import com.ridgecast.weather.util.WeatherDataValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 示例数据（世界地图中心点 · 几内亚湾）与**真实接口同口径**的回归测试。
 *
 * 真实接口字段一旦升级，[WeatherDataValidator] 同步升级，
 * 本测试会立刻指出示例数据缺了什么——不需要人工逐字段核对。
 */
class MockWeatherDataTest {

    private val sample = MockWeatherData.createSampleWeather()

    @Test
    fun `sample data passes the shared completeness validator`() {
        val report = WeatherDataValidator.validate(sample)
        assertTrue(
            "示例数据应零缺失，实际缺失：${report.missing}",
            report.missing.isEmpty()
        )
        assertTrue("应检查到足够多的字段", report.checked > 300)
    }

    @Test
    fun `sample location is the world map centre`() {
        assertEquals(0.0, MockWeatherData.MAP_CENTER_LAT, 0.0)
        assertEquals(0.0, MockWeatherData.MAP_CENTER_LON, 0.0)
        assertEquals("几内亚湾", MockWeatherData.DEFAULT_CITY_NAME)
        assertEquals(0.0, sample.location?.get(0) ?: -1.0, 1e-9)
        assertEquals(0.0, sample.location?.get(1) ?: -1.0, 1e-9)
    }

    @Test
    fun `realtime is complete`() {
        val realtime = sample.result?.realtime
        assertNotNull(realtime)
        assertNotNull(realtime?.temperature)
        assertNotNull(realtime?.humidity)
        assertNotNull(realtime?.skycon)
        assertNotNull(realtime?.pressure)
        assertNotNull(realtime?.wind?.scale)
        assertNotNull(realtime?.air_quality?.aqi?.chn)
        assertNotNull(realtime?.air_quality?.aqi?.usa)
        assertNotNull(realtime?.life_index?.ultraviolet?.index)
    }

    @Test
    fun `hourly and daily series are complete and aligned`() {
        val result = sample.result!!
        val hourly = result.hourly!!
        assertEquals(24, hourly.temperature!!.size)
        assertEquals(hourly.temperature!!.size, hourly.skycon!!.size)
        assertEquals(hourly.temperature!!.size, hourly.wind!!.size)
        assertEquals(hourly.temperature!!.size, hourly.humidity!!.size)
        assertEquals(hourly.temperature!!.size, hourly.cloudrate!!.size)
        assertEquals(hourly.temperature!!.size, hourly.precipitation!!.size)
        assertEquals(hourly.temperature!!.size, hourly.air_quality!!.aqi!!.size)
        assertEquals(hourly.temperature!!.size, hourly.life_index!!.ultraviolet!!.size)

        val daily = result.daily!!
        assertEquals(10, daily.astro!!.size)
        assertEquals(daily.astro!!.size, daily.temperature!!.size)
        assertEquals(daily.astro!!.size, daily.skycon!!.size)
        assertEquals(daily.astro!!.size, daily.wind!!.size)
        assertEquals(daily.astro!!.size, daily.precipitation!!.size)
        assertEquals(daily.astro!!.size, daily.air_quality!!.aqi!!.size)
    }

    @Test
    fun `every daily astro entry carries all time fields`() {
        val astro = sample.result?.daily?.astro ?: emptyList()
        assertTrue("应有 10 天", astro.size == 10)
        astro.forEachIndexed { index, a ->
            val label = "day[$index]"
            assertNotNull("$label.sunrise", a.sunrise?.time)
            assertNotNull("$label.sunset", a.sunset?.time)
            assertNotNull("$label.moonrise", a.moonrise?.time)
            assertNotNull("$label.moonset", a.moonset?.time)
            assertNotNull("$label.solarNoon", a.solarNoon?.time)
            assertNotNull("$label.solarMidnight", a.solarMidnight?.time)
            assertNotNull("$label.civilDawn", a.civilDawn?.time)
            assertNotNull("$label.civilDusk", a.civilDusk?.time)
            assertNotNull("$label.astronomicalDawn", a.astronomicalDawn?.time)
            assertNotNull("$label.astronomicalDusk", a.astronomicalDusk?.time)
            assertNotNull("$label.nauticalDawn", a.nauticalDawn?.time)
            assertNotNull("$label.nauticalDusk", a.nauticalDusk?.time)
            assertNotNull("$label.moonTransit", a.moonTransit?.time)
            assertNotNull("$label.moonUnderfoot", a.moonUnderfoot?.time)
            assertNotNull("$label.moonPhase", a.moonPhase)
            // 时间格式必须是 HH:mm
            listOf(a.sunrise, a.sunset, a.moonrise, a.moonset, a.solarNoon).forEach {
                val time = it?.time ?: return@forEach
                assertTrue("$label 时间格式应为 HH:mm，实际 $time", Regex("^\\d{2}:\\d{2}$").matches(time))
            }
        }
    }

    @Test
    fun `equatorial location has near twelve hour day`() {
        val astro = sample.result?.daily?.astro?.first() ?: return
        val sunrise = astro.sunrise?.time?.split(":")?.map { it.toInt() } ?: return
        val sunset = astro.sunset?.time?.split(":")?.map { it.toInt() } ?: return
        val riseMinutes = sunrise[0] * 60 + sunrise[1]
        val setMinutes = sunset[0] * 60 + sunset[1]
        val dayLength = if (setMinutes >= riseMinutes) setMinutes - riseMinutes else 1440 - riseMinutes + setMinutes
        // 赤道全年昼夜等长（约 12 小时 ± 20 分钟）
        assertTrue("赤道昼夜应约 12 小时，实际 $dayLength 分钟", dayLength in 700..740)
    }

    @Test
    fun `astronomy snapshot is present`() {
        val astronomy = sample.result?.astronomy
        assertNotNull("示例数据应带天文快照", astronomy)
        assertNotNull(astronomy?.solarElevationDegrees)
        assertNotNull(astronomy?.solarAzimuthDegrees)
        assertNotNull(astronomy?.moonIllumination)
    }

    @Test
    fun `temperatures are plausible for the gulf of guinea`() {
        val realtime = sample.result?.realtime
        val temp = realtime?.temperature ?: 0.0
        assertTrue("赤道海域气温应在 24~32℃，实际 $temp", temp in 24.0..32.0)
        // 口径与 v1 接口一致：湿度/云量是 0..1 的比例
        val humidity = realtime?.humidity ?: 0.0
        assertTrue("赤道海域湿度应 > 60%，实际 $humidity", humidity in 0.60..1.0)
        val cloud = realtime?.cloudrate ?: 0.0
        assertTrue("云量应在 0..1", cloud in 0.0..1.0)
    }
}
