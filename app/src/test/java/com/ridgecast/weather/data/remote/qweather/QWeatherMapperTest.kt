package com.ridgecast.weather.data.remote.qweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QWeatherMapperTest {

    @Test
    fun `mapAirQuality calculates sub-indices and overall AQI using US EPA standard`() {
        // 模拟用户场景：和风国内站点返回浓度客观数据，同时带了国标分指数（subIndexes，qaqi 均为黄色良）
        val mockAirResponse = QWeatherAirResponse(
            indexes = listOf(
                QWeatherAirIndex(code = "qaqi", name = "中国综合指数", aqi = 90.0, category = "良")
            ),
            pollutants = listOf(
                QWeatherPollutant(
                    code = "pm2p5",
                    name = "PM2.5",
                    concentration = QWeatherConcentration(value = 43.0, unit = "μg/m3"),
                    subIndexes = listOf(QWeatherSubIndex(code = "qaqi", aqi = 60.0))
                ),
                QWeatherPollutant(
                    code = "pm10",
                    name = "PM10",
                    concentration = QWeatherConcentration(value = 83.0, unit = "μg/m3"),
                    subIndexes = listOf(QWeatherSubIndex(code = "qaqi", aqi = 67.0))
                ),
                QWeatherPollutant(
                    code = "o3",
                    name = "O3",
                    concentration = QWeatherConcentration(value = 192.0, unit = "μg/m3"),
                    subIndexes = listOf(QWeatherSubIndex(code = "qaqi", aqi = 90.0))
                )
            )
        )

        val result = QWeatherMapper.mapAirQuality(mockAirResponse)
        assertNotNull(result)

        // 客观浓度数据正确映射
        assertEquals(43.0, result!!.pm25)
        assertEquals(83.0, result.pm10)
        assertEquals(192.0, result.o3)

        // 分项 IAQI 必须按美标计算，不受国标 subIndexes 干扰
        // PM2.5 43 μg/m³ -> 美标 IAQI 119 (轻度不健康)
        assertEquals(119, result.pm25Iaqi)
        // PM10 83 μg/m³ -> 美标 IAQI 65 (中等)
        assertEquals(65, result.pm10Iaqi)
        // O3 192 μg/m³ ≈ 97.8 ppb -> 美标 IAQI 179 (不健康)
        assertEquals(179, result.o3Iaqi)

        // 综合 AQI 取各分项美标最大值
        assertEquals(179.0, result.aqi?.usa)
        assertEquals("不健康", result.description?.usa)
        assertEquals("O₃", result.primaryPollutant)
    }
}
