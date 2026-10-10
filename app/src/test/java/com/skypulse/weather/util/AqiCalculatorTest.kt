package com.skypulse.weather.util

import org.junit.Assert.*
import org.junit.Test

class AqiCalculatorTest {

    @Test
    fun `calculate returns null when all pollutants are null`() {
        assertNull(AqiCalculator.calculate())
    }

    @Test
    fun `calculate handles Good air quality correctly`() {
        val result = AqiCalculator.calculate(pm25 = 5.0, pm10 = 20.0)
        assertNotNull(result)
        assertTrue(result!!.aqi in 0..50)
        assertEquals("优", result.category)
        // AQI <= 50 时无首要污染物
        assertNull(result.primaryPollutant)
    }

    @Test
    fun `calculate handles Moderate air quality correctly`() {
        val result = AqiCalculator.calculate(pm25 = 20.0)
        assertNotNull(result)
        assertTrue(result!!.aqi in 51..100)
        assertEquals("中等", result.category)
        assertEquals("PM2.5", result.primaryPollutant)
    }

    @Test
    fun `calculate correctly identifies dominant primary pollutant`() {
        // PM2.5 = 15.0 (AQI ~ 62)
        // PM10 = 200.0 (falls in 155..254, AQI ~ 124)
        val result = AqiCalculator.calculate(pm25 = 15.0, pm10 = 200.0)
        assertNotNull(result)
        assertTrue(result!!.aqi in 101..150)
        assertEquals("轻度不健康", result.category)
        assertEquals("PM10", result.primaryPollutant)
    }

    @Test
    fun `calculate handles higher severity categories`() {
        // PM2.5 = 80.0 -> 不健康 (151..200)
        val resultUnhealthy = AqiCalculator.calculate(pm25 = 80.0)
        assertNotNull(resultUnhealthy)
        assertEquals("不健康", resultUnhealthy!!.category)
        assertTrue(resultUnhealthy.aqi in 151..200)

        // PM2.5 = 180.0 -> 重度不健康 (201..300)
        val resultVeryUnhealthy = AqiCalculator.calculate(pm25 = 180.0)
        assertNotNull(resultVeryUnhealthy)
        assertEquals("重度不健康", resultVeryUnhealthy!!.category)
        assertTrue(resultVeryUnhealthy.aqi in 201..300)

        // PM2.5 = 350.0 -> 危险 (301+)
        val resultHazardous = AqiCalculator.calculate(pm25 = 350.0)
        assertNotNull(resultHazardous)
        assertEquals("危险", resultHazardous!!.category)
        assertTrue(resultHazardous.aqi > 300)
    }

    @Test
    fun `calculate handles gaseous pollutants with conversion`() {
        // O3 in ug/m3: 150 ug/m3 / 1.963 ≈ 76 ppb (falls in 71..85, AQI ~ 116)
        val result = AqiCalculator.calculate(o3 = 150.0)
        assertNotNull(result)
        assertTrue(result!!.aqi in 101..150)
        assertEquals("O₃", result.primaryPollutant)
    }
}
