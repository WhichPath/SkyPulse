package com.ridgecast.weather.util

import org.junit.Assert.*
import org.junit.Test

class AqhiCalculatorTest {

    @Test
    fun `calculate returns null when all pollutants are null`() {
        assertNull(AqhiCalculator.calculate())
    }

    @Test
    fun `calculate returns low risk level for clean air`() {
        val result = AqhiCalculator.calculate(
            pm25 = 5.0,
            pm10 = 10.0,
            o3 = 10.0,
            no2 = 5.0,
            so2 = 2.0
        )
        assertNotNull(result)
        assertTrue(result!!.index in 1..3)
        assertEquals("低", result.category)
        assertTrue(result.addedRisk <= 5.64)
    }

    @Test
    fun `calculate returns moderate risk level for moderate pollution`() {
        // PM2.5 = 45.0, O3 = 50.0 ppb (~98 ug/m3), NO2 = 40.0 ppb (~75 ug/m3)
        val result = AqhiCalculator.calculate(
            pm25 = 45.0,
            o3 = 50.0,
            no2 = 40.0
        )
        assertNotNull(result)
        assertTrue(result!!.index in 4..6)
        assertEquals("中", result.category)
        assertTrue(result.addedRisk in 5.64..11.29)
    }

    @Test
    fun `calculate returns high or very high risk level for heavy combined pollution`() {
        // High multi-pollutant exposure
        val result = AqhiCalculator.calculate(
            pm25 = 120.0,
            pm10 = 250.0,
            o3 = 90.0,
            no2 = 80.0,
            so2 = 50.0
        )
        assertNotNull(result)
        assertTrue(result!!.index >= 7)
        assertTrue(result.category in listOf("高", "甚高", "严重"))
        assertTrue(result.addedRisk > 11.29)
    }

    @Test
    fun `calculate handles serious level above 10`() {
        // Severe wildfire / heavy industrial event
        val result = AqhiCalculator.calculate(
            pm25 = 500.0,
            pm10 = 800.0,
            o3 = 200.0,
            no2 = 200.0,
            so2 = 100.0
        )
        assertNotNull(result)
        assertEquals(11, result!!.index)
        assertEquals("10+", result.displayValue)
        assertEquals("严重", result.category)
        assertTrue(result.addedRisk > 19.37)
    }

    @Test
    fun `calculate avoids multiplying when gas concentration is already in ug per cubic meter`() {
        val resultUgm3 = AqhiCalculator.calculate(
            pm25 = 20.0,
            o3 = 99.0,
            o3Unit = "μg/m³"
        )
        assertNotNull(resultUgm3)
        // PM2.5 = 20 (ar = 0.437%), O3 = 99 ug/m3 (ar = 4.958%), total = 5.395% -> 低 (index 3)
        // 若误按 ppb 翻倍 (194 ug/m3)，则 O3 ar = 9.94%，总计 10.38% -> 中 (index 6)
        assertEquals(3, resultUgm3!!.index)
        assertEquals("低", resultUgm3.category)
    }
}
