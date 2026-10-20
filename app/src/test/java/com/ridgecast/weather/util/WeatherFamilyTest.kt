package com.ridgecast.weather.util

import androidx.compose.ui.graphics.Color
import com.ridgecast.weather.ui.theme.CloudyGradient
import com.ridgecast.weather.ui.theme.PartialCloudGradient
import com.ridgecast.weather.ui.theme.RainyGradient
import com.ridgecast.weather.ui.theme.SnowyGradient
import com.ridgecast.weather.ui.theme.SunnyGradient
import com.ridgecast.weather.ui.theme.ThunderGradient
import com.ridgecast.weather.ui.theme.WindyGradient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天气族归类与背景配色分派回归测试。
 *
 * 背景事故：`THUNDER_SHOWER` / `SLEET` / `HAIL` 因为不含任何被关键词链命中的词，
 * 在渐变层落到了 `else -> 晴天蓝`（闪电配晴空），在玻璃配色层被判为非降水
 * 而套用了浅色玻璃。这里逐条钉死。
 */
class WeatherFamilyTest {

    // ============ classifyWeather ============

    @Test
    fun `classifyWeather maps clear variants to Clear`() {
        assertEquals(WeatherFamily.Clear, classifyWeather("CLEAR_DAY"))
        assertEquals(WeatherFamily.Clear, classifyWeather("CLEAR_NIGHT"))
        assertEquals(WeatherFamily.Clear, classifyWeather("CLEAR"))
    }

    @Test
    fun `classifyWeather maps partly cloudy before cloudy`() {
        // 「PARTLY_CLOUDY」含子串「CLOUDY」，必须先判多云
        assertEquals(WeatherFamily.PartlyCloudy, classifyWeather("PARTLY_CLOUDY_DAY"))
        assertEquals(WeatherFamily.PartlyCloudy, classifyWeather("PARTLY_CLOUDY_NIGHT"))
        assertEquals(WeatherFamily.Cloudy, classifyWeather("CLOUDY"))
    }

    @Test
    fun `classifyWeather maps thunder shower to Thunder`() {
        assertEquals(WeatherFamily.Thunder, classifyWeather("THUNDER_SHOWER"))
    }

    @Test
    fun `classifyWeather maps rain variants to Rain`() {
        assertEquals(WeatherFamily.Rain, classifyWeather("LIGHT_RAIN"))
        assertEquals(WeatherFamily.Rain, classifyWeather("MODERATE_RAIN"))
        assertEquals(WeatherFamily.Rain, classifyWeather("HEAVY_RAIN"))
        assertEquals(WeatherFamily.Rain, classifyWeather("STORM_RAIN"))
        assertEquals(WeatherFamily.Rain, classifyWeather("SHOWER_RAIN"))
    }

    @Test
    fun `classifyWeather maps snow variants to Snow`() {
        assertEquals(WeatherFamily.Snow, classifyWeather("LIGHT_SNOW"))
        assertEquals(WeatherFamily.Snow, classifyWeather("MODERATE_SNOW"))
        assertEquals(WeatherFamily.Snow, classifyWeather("HEAVY_SNOW"))
        assertEquals(WeatherFamily.Snow, classifyWeather("STORM_SNOW"))
        assertEquals(WeatherFamily.Snow, classifyWeather("SHOWER_SNOW"))
    }

    @Test
    fun `classifyWeather maps sleet and hail to their own families`() {
        assertEquals(WeatherFamily.Sleet, classifyWeather("SLEET"))
        assertEquals(WeatherFamily.Hail, classifyWeather("HAIL"))
    }

    @Test
    fun `classifyWeather maps haze fog dust sand to Haze`() {
        assertEquals(WeatherFamily.Haze, classifyWeather("LIGHT_HAZE"))
        assertEquals(WeatherFamily.Haze, classifyWeather("MODERATE_HAZE"))
        assertEquals(WeatherFamily.Haze, classifyWeather("HEAVY_HAZE"))
        assertEquals(WeatherFamily.Haze, classifyWeather("FOG"))
        assertEquals(WeatherFamily.Haze, classifyWeather("DUST"))
        assertEquals(WeatherFamily.Haze, classifyWeather("SAND"))
    }

    @Test
    fun `classifyWeather maps wind and unknown`() {
        assertEquals(WeatherFamily.Wind, classifyWeather("WIND"))
        assertEquals(WeatherFamily.Unknown, classifyWeather(null))
        assertEquals(WeatherFamily.Unknown, classifyWeather(""))
        assertEquals(WeatherFamily.Unknown, classifyWeather("SOMETHING_NEW"))
    }

    // ============ isPrecipitating ============

    @Test
    fun `isPrecipitating covers thunder sleet and hail`() {
        assertTrue(classifyWeather("THUNDER_SHOWER").isPrecipitating)
        assertTrue(classifyWeather("SLEET").isPrecipitating)
        assertTrue(classifyWeather("HAIL").isPrecipitating)
        assertTrue(classifyWeather("MODERATE_RAIN").isPrecipitating)
        assertTrue(classifyWeather("MODERATE_SNOW").isPrecipitating)
    }

    @Test
    fun `isPrecipitating is false for clear cloudy haze and wind`() {
        assertFalse(classifyWeather("CLEAR_DAY").isPrecipitating)
        assertFalse(classifyWeather("PARTLY_CLOUDY_DAY").isPrecipitating)
        assertFalse(classifyWeather("CLOUDY").isPrecipitating)
        assertFalse(classifyWeather("LIGHT_HAZE").isPrecipitating)
        assertFalse(classifyWeather("WIND").isPrecipitating)
        assertFalse(classifyWeather(null).isPrecipitating)
    }

    // ============ getWeatherGradient 分派 ============

    @Test
    fun `thunder shower no longer falls back to sunny gradient`() {
        assertEquals(ThunderGradient, WeatherUtils.getWeatherGradient("THUNDER_SHOWER", isDay = true))
        assertNotEquals(
            SunnyGradient,
            WeatherUtils.getWeatherGradient("THUNDER_SHOWER", isDay = true)
        )
    }

    @Test
    fun `sleet and hail no longer fall back to sunny gradient`() {
        assertNotEquals(
            SunnyGradient,
            WeatherUtils.getWeatherGradient("SLEET", isDay = true)
        )
        assertNotEquals(
            SunnyGradient,
            WeatherUtils.getWeatherGradient("HAIL", isDay = true)
        )
    }

    @Test
    fun `every known skycon maps to a distinct non-sunny family gradient`() {
        assertEquals(SunnyGradient, WeatherUtils.getWeatherGradient("CLEAR_DAY", isDay = true))
        assertEquals(
            PartialCloudGradient,
            WeatherUtils.getWeatherGradient("PARTLY_CLOUDY_DAY", isDay = true)
        )
        assertEquals(CloudyGradient, WeatherUtils.getWeatherGradient("CLOUDY", isDay = true))
        assertEquals(RainyGradient, WeatherUtils.getWeatherGradient("MODERATE_RAIN", isDay = true))
        assertEquals(SnowyGradient, WeatherUtils.getWeatherGradient("MODERATE_SNOW", isDay = true))
        assertEquals(WindyGradient, WeatherUtils.getWeatherGradient("WIND", isDay = true))
    }

    @Test
    fun `all skycon in the qweather vocabulary produce a five color gradient`() {
        val skycons = listOf(
            "CLEAR_DAY", "CLEAR_NIGHT", "CLEAR",
            "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT", "PARTLY_CLOUDY",
            "CLOUDY",
            "LIGHT_HAZE", "MODERATE_HAZE", "HEAVY_HAZE",
            "LIGHT_RAIN", "MODERATE_RAIN", "HEAVY_RAIN", "STORM_RAIN", "SHOWER_RAIN",
            "LIGHT_SNOW", "MODERATE_SNOW", "HEAVY_SNOW", "STORM_SNOW", "SHOWER_SNOW",
            "THUNDER_SHOWER", "SLEET", "HAIL",
            "FOG", "DUST", "SAND", "WIND"
        )
        skycons.forEach { skycon ->
            listOf(true, false).forEach { isDay ->
                val gradient = WeatherUtils.getWeatherGradient(skycon, isDay = isDay)
                assertEquals("skycon=$skycon isDay=$isDay", 5, gradient.size)
            }
        }
    }

    @Test
    fun `thunder gradient is darker than rain gradient`() {
        // 雷暴天空必须比普通雨更沉：整体亮度（绿+蓝通道均值）更低
        fun brightness(colors: List<Color>): Float =
            colors.fold(0f) { acc, c -> acc + (c.red + c.green + c.blue) / 3f } / colors.size

        assertTrue(brightness(ThunderGradient) < brightness(RainyGradient))
    }

    // ============ 玻璃配色：降水判定 ============

    @Test
    fun `thunder shower uses dark glass instead of light glass`() {
        val dayTheme = WeatherUtils.getWeatherTheme("THUNDER_SHOWER", isDay = true)
        val clearTheme = WeatherUtils.getWeatherTheme("CLEAR_DAY", isDay = true)

        // 深色玻璃（isLight = false）vs 晴天浅色玻璃（isLight = true）
        assertFalse(dayTheme.glass.isLight)
        assertTrue(clearTheme.glass.isLight)
    }

    @Test
    fun `sleet and hail also use dark glass`() {
        assertFalse(WeatherUtils.getWeatherTheme("SLEET", isDay = true).glass.isLight)
        assertFalse(WeatherUtils.getWeatherTheme("HAIL", isDay = true).glass.isLight)
    }

    // ============ skyGradientColorStops 锚点 ============

    @Test
    fun `skyGradientColorStops uses the non equidistant sky anchors`() {
        val stops = skyGradientColorStops(listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta))
        assertEquals(5, stops.size)
        assertEquals(SkyGradientStops, stops.map { it.first })
        // 锚点必须严格递增，否则 Brush.verticalGradient 会抛异常
        stops.toList().zipWithNext { a, b -> assertTrue(a.first < b.first) }
    }

    @Test
    fun `skyGradientColorStops falls back to even distribution on size mismatch`() {
        val four = skyGradientColorStops(listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow))
        assertEquals(4, four.size)
        assertEquals(listOf(0f, 1f / 3f, 2f / 3f, 1f), four.map { it.first })
    }

    @Test
    fun `skyGradientColorStops handles empty list`() {
        assertEquals(1, skyGradientColorStops(emptyList()).size)
    }
}
