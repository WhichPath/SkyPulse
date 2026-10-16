package com.ridgecast.weather.data.remote

import com.ridgecast.weather.model.WeatherResponse

/**
 * 天气 API 的抽象接口。
 *
 * 当前实现：QWeatherApiService（和风天气）。
 */
interface WeatherApiService {

    /**
     * 获取天气数据。
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @param alert 是否包含预警
     * @param hourlySteps 小时预报步数
     * @return 天气响应
     */
    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        alert: Boolean = true,
        hourlySteps: Int = 24
    ): WeatherResponse
}
