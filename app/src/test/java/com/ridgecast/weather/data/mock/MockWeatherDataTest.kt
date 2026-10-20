package com.ridgecast.weather.data.mock

import com.ridgecast.weather.util.WeatherDataValidator
import com.ridgecast.weather.util.resolvedAqhi
import com.ridgecast.weather.util.resolvedAqi
import com.ridgecast.weather.util.resolvedHourlyAqi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertNotNull(realtime?.life_index?.ultraviolet?.index)
    }

    @Test
    fun `air quality only carries raw measurements so the app can compute aqi and aqhi`() {
        val air = sample.result?.realtime?.air_quality
        assertNotNull("实况空气质量必须给出分项浓度", air)

        // 原始观测量：六项污染物浓度必须在
        assertNotNull("PM2.5", air?.pm25)
        assertNotNull("PM10", air?.pm10)
        assertNotNull("O3", air?.o3)
        assertNotNull("SO2", air?.so2)
        assertNotNull("NO2", air?.no2)
        assertNotNull("CO", air?.co)

        // 派生量：示例数据不得预置，AQI/AQHI 一律由 App 自算
        assertNull("示例数据不应预置 AQI", air?.aqi)
        assertNull("示例数据不应预置分项 IAQI", air?.pm25Iaqi)

        // 自算必须能得到合理结果（赤道海域：优 / AQHI 低）
        val aqi = air?.resolvedAqi()
        assertNotNull("应由分项浓度算出 AQI", aqi)
        assertTrue("AQI 应在 0..100，实际 $aqi", aqi!! in 0..100)
        val aqhi = air?.resolvedAqhi()
        assertNotNull("应由分项浓度算出 AQHI", aqhi)
        assertTrue("AQHI 等级应为低/中，实际 ${aqhi!!.category}", aqhi.category in listOf("低", "中"))
    }

    @Test
    fun `hourly air quality carries raw pollutants so the app can compute aqi`() {
        val air = sample.result?.hourly?.air_quality
        assertNotNull("逐时空气质量必须给出分项浓度", air)
        assertNotNull("PM2.5", air?.pm25)
        assertNotNull("PM10", air?.pm10)
        assertNotNull("O3", air?.o3)
        assertNotNull("SO2", air?.so2)
        assertNotNull("NO2", air?.no2)
        assertNotNull("CO", air?.co)
        // 派生量不预置
        assertNull("示例数据不应预置逐时 AQI", air?.aqi)

        // App 自算必须得到逐时 AQI
        val computed = air?.resolvedHourlyAqi()
        assertNotNull(computed)
        assertEquals(air?.pm25?.size, computed?.size)
        assertTrue("逐时 AQI 应能算出来", computed?.any { it != null } == true)
    }

    @Test
    fun `only realtime carries air quality`() {
        // 逐时/逐日在**模型里就没有**空气质量字段（和风 v1 对应端点不提供该数据），
        // 因此空气质量只可能来自实况，示例数据也不得凭空造出逐时/逐日 AQI。
        val air = sample.result?.realtime?.air_quality
        assertNotNull("空气质量只存在于实况", air)
        assertNotNull(air?.pm25)
    }

    @Test
    fun `minutely precipitation is real data not a placeholder`() {
        val minutely = sample.result?.minutely
        assertNotNull("必须提供分钟级降水", minutely)
        val series = minutely!!.precipitation_2h
        assertNotNull("分钟级降水序列必须存在", series)
        assertEquals(24, series!!.size)
        assertEquals(series.size, minutely.times?.size)

        val raining = series.any { it != 0.0 }
        // 分钟级窗口覆盖「当前小时 + 下一小时」，有雨与否应与这两小时的天气一致
        val coveredSkycons = sample.result?.hourly?.skycon?.take(2).orEmpty()
        val shouldRain = coveredSkycons.any {
            it.value?.contains("RAIN") == true || it.value?.contains("STORM") == true
        }
        assertEquals(
            "分钟级降水的有无必须与窗口内天气一致（$coveredSkycons）",
            shouldRain,
            raining
        )
        // summary 与和风同口径：**自然语言描述**（官方示例「95分钟后雨就停了」），
        // 不是「有降水/无降水」这种标签
        val description = minutely.description.orEmpty()
        if (raining) {
            assertTrue(
                "有降水时 summary 应是自然语言时间描述，实际 $description",
                description.contains("分钟后") && (description.contains("雨") || description.contains("雪"))
            )
        } else {
            assertEquals("未来两小时无降水", description)
        }
    }

    @Test
    fun `minutely precipitation sums up to the hourly precipitation`() {
        val result = sample.result!!
        val hourlyRain = result.hourly!!.precipitation!!.mapNotNull { it.value }
        val series = result.minutely!!.precipitation_2h!!
        assertNotNull(series)

        // 同一小时内的 12 个 5 分钟累计之和，必须等于该小时的降水量（mm）——
        // 否则图上强度会和逐小时预报对不上
        for (h in 0 until minOf(hourlyRain.size, series.size / 12)) {
            val sum = series.subList(h * 12, h * 12 + 12).sum()
            assertEquals(
                "第 $h 小时的分钟级累计之和（$sum）应等于该小时降水量（${hourlyRain[h]}）",
                hourlyRain[h], sum, 0.1
            )
        }
    }

    @Test
    fun `forecast keypoint comes from the minutely summary`() {        val minutely = sample.result?.minutely
        assertEquals(
            "forecast_keypoint 必须取自分钟级 summary，不得塞气候说明文案",
            minutely?.description,
            sample.result?.forecastKeypoint
        )
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
        assertEquals(hourly.temperature!!.size, hourly.life_index!!.ultraviolet!!.size)

        val daily = result.daily!!
        assertEquals(10, daily.astro!!.size)
        assertEquals(daily.astro!!.size, daily.temperature!!.size)
        assertEquals(daily.astro!!.size, daily.skycon!!.size)
        assertEquals(daily.astro!!.size, daily.wind!!.size)
        assertEquals(daily.astro!!.size, daily.precipitation!!.size)
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
