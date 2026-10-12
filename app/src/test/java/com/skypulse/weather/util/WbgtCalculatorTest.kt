package com.skypulse.weather.util

import org.junit.Assert.*
import org.junit.Test

class WbgtCalculatorTest {

    @Test
    fun testTypicalSummerMiddayCondition() {
        // 典型夏日中午高温强日照情境：气温 35℃，湿度 60%，风速 10km/h (约 2.78m/s)，少云 20%
        val result = WbgtCalculator.calculate(
            temperature = 35.0,
            humidity = 60.0,
            windSpeedKmh = 10.0,
            pressurePa = 101325.0,
            cloudCover = 0.20,
            latitude = 39.9,
            longitude = 116.4,
            epochSeconds = 1753070400L // 7月中旬正午
        )

        assertNotNull(result)
        result?.let {
            // 夏日日照下黑球吸热明显，Tg 应高于 Ta
            assertTrue("Tg (${it.tg}) should be higher than Ta (35.0)", it.tg > 35.0)
            // 自然湿球 Tnwb 应低于 Ta
            assertTrue("Tnwb (${it.tnwb}) should be lower than Ta (35.0)", it.tnwb < 35.0)
            // WBGT 应在合理的高热风险区间内 (28℃ ~ 36℃)
            assertTrue("WBGT (${it.wbgt}) should be in range 28..36", it.wbgt in 28.0..36.0)
            // 误差线应在合理置信区间内 (0.5℃ ~ 2.0℃)
            assertTrue("Uncertainty (${it.uncertainty}) should be in range 0.5..2.0", it.uncertainty in 0.5..2.0)
            // 太阳辐射应大于 0
            assertTrue("Solar radiation (${it.solarRadiation}) should be positive", it.solarRadiation > 100.0)
        }
    }

    @Test
    fun testNighttimeCondition() {
        // 夜间无辐射情境：气温 25℃，湿度 80%，风速 5km/h，云量 50%
        val result = WbgtCalculator.calculate(
            temperature = 25.0,
            humidity = 80.0,
            windSpeedKmh = 5.0,
            pressurePa = 101325.0,
            cloudCover = 0.50,
            latitude = 39.9,
            longitude = 116.4,
            epochSeconds = 1753128000L // 午夜
        )

        assertNotNull(result)
        result?.let {
            // 夜间太阳辐射应为 0
            assertEquals(0.0, it.solarRadiation, 0.001)
            // 夜间无辐射加热，Tg 应非常接近甚至略低于 Ta
            assertTrue("Nighttime Tg (${it.tg}) should be close to Ta (25.0)", it.tg <= 25.1)
            // 夜间由于无辐射误差，总不确定度应显著低于白天
            assertTrue("Nighttime uncertainty (${it.uncertainty}) should be low (< 1.2)", it.uncertainty < 1.2)
        }
    }

    @Test
    fun testHighHumidityAndCloudyDynamicUncertainty() {
        // 高湿阴天：湿度 90% (传感器误差大)，满云 100% (云量误差大)
        val cloudyResult = WbgtCalculator.calculate(
            temperature = 30.0,
            humidity = 90.0,
            windSpeedKmh = 3.0,
            pressurePa = 101325.0,
            cloudCover = 1.0,
            latitude = 31.2,
            longitude = 121.5,
            epochSeconds = 1753070400L
        )

        // 清凉少云：湿度 40%，云量 10%
        val clearResult = WbgtCalculator.calculate(
            temperature = 30.0,
            humidity = 40.0,
            windSpeedKmh = 12.0,
            pressurePa = 101325.0,
            cloudCover = 0.1,
            latitude = 31.2,
            longitude = 121.5,
            epochSeconds = 1753070400L
        )

        assertNotNull(cloudyResult)
        assertNotNull(clearResult)
        // 高湿微风浓云环境下，动态不确定度应明显高于少云大风环境
        assertTrue(
            "Cloudy humid uncertainty (${cloudyResult!!.uncertainty}) should be higher than clear windy (${clearResult!!.uncertainty})",
            cloudyResult.uncertainty >= clearResult.uncertainty
        )
    }

    @Test
    fun testNullInputsReturnNull() {
        assertNull(WbgtCalculator.calculate(null, 50.0, 5.0))
        assertNull(WbgtCalculator.calculate(25.0, null, 5.0))
    }

    @Test
    fun testDefensivePressureAndCloudCoverUnitHandling() {
        // 当 pressurePa 传入 hPa (1013.25) 与传入 Pa (101325.0) 时应得到相同结果
        val resPa = WbgtCalculator.calculate(30.0, 50.0, 10.0, pressurePa = 101325.0, cloudCover = 0.5)
        val resHpa = WbgtCalculator.calculate(30.0, 50.0, 10.0, pressurePa = 1013.25, cloudCover = 0.5)
        assertNotNull(resPa)
        assertNotNull(resHpa)
        assertEquals(resPa!!.wbgt, resHpa!!.wbgt, 0.05)

        // 当 cloudCover 传入百分比 (50.0) 与传入小数 (0.5) 时应得到相同结果
        val resCcPercent = WbgtCalculator.calculate(30.0, 50.0, 10.0, pressurePa = 101325.0, cloudCover = 50.0)
        assertNotNull(resCcPercent)
        assertEquals(resPa.wbgt, resCcPercent!!.wbgt, 0.05)
    }
}
