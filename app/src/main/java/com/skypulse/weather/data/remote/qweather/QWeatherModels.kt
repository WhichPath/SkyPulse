package com.skypulse.weather.data.remote.qweather

import com.squareup.moshi.JsonClass

/**
 * 和风天气 API 响应模型。
 *
 * 数据源：https://dev.qweather.com/
 * 认证方式：JWT (Ed25519 签名)，通过 Authorization: Bearer 头传递。
 *
 * 注意单位差异（和风 vs 彩云）：
 * - 湿度：和风为百分比(0-100)，彩云为小数(0-1) -> 映射时 /100
 * - 气压：和风为 hPa，彩云为 Pa -> 映射时 *100
 * - 能见度：和风为 km，彩云为 m -> 映射时 *1000
 * - 云量：和风为百分比(0-100)，彩云为小数(0-1) -> 映射时 /100
 * - 风速：和风为 km/h，彩云也为 km/h -> 无需转换
 * - 温度：均为摄氏度 -> 无需转换
 */

// ============ Minutely Precipitation (分钟级降水, v7 API) ============
// 和风无 v1 版本，保留 v7 端点 /v7/minutely/5m

@JsonClass(generateAdapter = true)
data class QWeatherMinutelyResponse(
    val code: String? = null,
    val summary: String? = null,
    val minutely: List<QWeatherMinutelyItem>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherMinutelyItem(
    val fxTime: String? = null,
    val precip: String? = null,
    val type: String? = null
)

// ============ Weather Warning (天气预警, v1 API) ============

@JsonClass(generateAdapter = true)
data class QWeatherWarningResponse(
    val metadata: QWeatherWarningMeta? = null,
    val alerts: List<QWeatherAlert>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherWarningMeta(
    val zeroResult: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherAlert(
    val id: String? = null,
    val senderName: String? = null,
    val issuedTime: String? = null,
    val eventType: QWeatherEventType? = null,
    val severity: String? = null,
    val icon: String? = null,
    val color: QWeatherAlertColor? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherEventType(
    val name: String? = null,
    val code: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherAlertColor(
    val code: String? = null,
    val red: Int? = null,
    val green: Int? = null,
    val blue: Int? = null,
    val alpha: Double? = null
)

// ============ Air Quality (空气质量, v1 API) ============

@JsonClass(generateAdapter = true)
data class QWeatherAirResponse(
    val indexes: List<QWeatherAirIndex>? = null,
    val pollutants: List<QWeatherPollutant>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherAirIndex(
    val code: String? = null,
    val name: String? = null,
    val aqi: Double? = null,
    val category: String? = null,
    val primaryPollutant: QWeatherAirPrimaryPollutant? = null,
    val health: QWeatherAirHealth? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherAirPrimaryPollutant(
    val code: String? = null,
    val name: String? = null,
    val fullName: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherAirHealth(
    val effect: String? = null,
    val advice: QWeatherAirAdvice? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherAirAdvice(
    val generalPopulation: String? = null,
    val sensitivePopulation: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherPollutant(
    val code: String? = null,
    val name: String? = null,
    val fullName: String? = null,
    val concentration: QWeatherConcentration? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherConcentration(
    val value: Double? = null,
    val unit: String? = null
)

// ============ GeoAPI (城市搜索, v2 API) ============

@JsonClass(generateAdapter = true)
data class QWeatherGeoResponse(
    val code: String? = null,
    val location: List<QWeatherGeoLocation>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherGeoLocation(
    val name: String? = null,
    val id: String? = null,
    val lat: String? = null,
    val lon: String? = null,
    val adm2: String? = null,
    val adm1: String? = null,
    val country: String? = null
)

// ============ v1 Weather Current (实况天气, 新版 API) ============
// 端点: /weather/v1/current/{lat}/{lon}
// 与 v7 的主要区别: 结构化对象（非字符串）、含 uvIndex/windGust、单位为 m/s（非 km/h）

@JsonClass(generateAdapter = true)
data class QWeatherV1NowResponse(
    val metadata: QWeatherV1Metadata? = null,
    val condition: QWeatherV1Condition? = null,
    val temperature: QWeatherV1Metric? = null,
    val feelsLike: QWeatherV1Metric? = null,
    val humidity: Double? = null,
    val wind: QWeatherV1Wind? = null,
    val windGust: QWeatherV1Metric? = null,
    val precipitation: QWeatherV1Precipitation? = null,
    val pressure: QWeatherV1Metric? = null,
    val visibility: QWeatherV1Metric? = null,
    val dewPoint: QWeatherV1Metric? = null,
    val cloudCover: Double? = null,
    val uvIndex: Double? = null
)

// ============ v1 Weather Hourly (逐小时预报, 新版 API) ============
// 端点: /weather/v1/hourly/{lat}/{lon}
// 与 v7 的主要区别: 结构化对象（非字符串）、含 uvIndex、单位为 m/s（非 km/h）

@JsonClass(generateAdapter = true)
data class QWeatherV1HourlyResponse(
    val metadata: QWeatherV1Metadata? = null,
    val hours: List<QWeatherV1Hour>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Hour(
    val forecastTime: String? = null,
    val condition: QWeatherV1Condition? = null,
    val temperature: QWeatherV1Metric? = null,
    val feelsLike: QWeatherV1Metric? = null,
    val humidity: Double? = null,
    val wind: QWeatherV1Wind? = null,
    val windGust: QWeatherV1Metric? = null,
    val precipitation: QWeatherV1Precipitation? = null,
    val pressure: QWeatherV1Metric? = null,
    val visibility: QWeatherV1Metric? = null,
    val dewPoint: QWeatherV1Metric? = null,
    val cloudCover: Double? = null,
    val uvIndex: Double? = null
)

// ============ v1 Weather Daily (逐日预报, 新版 API) ============
// 端点: /weather/v1/daily/{lat}/{lon}
// 最多 10 天（v7 为 15d），含 uvIndexMax、白天/夜间分段预报、完整天文数据

@JsonClass(generateAdapter = true)
data class QWeatherV1DailyResponse(
    val metadata: QWeatherV1Metadata? = null,
    val days: List<QWeatherV1Day>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Day(
    val forecastStartTime: String? = null,
    val forecastEndTime: String? = null,
    val astro: QWeatherV1Astro? = null,
    val temperatureMax: QWeatherV1Metric? = null,
    val temperatureMin: QWeatherV1Metric? = null,
    val temperatureAvg: QWeatherV1Metric? = null,
    val uvIndexMax: Double? = null,
    val daytime: QWeatherV1DayPart? = null,
    val nighttime: QWeatherV1DayPart? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Astro(
    val sunrise: String? = null,
    val sunset: String? = null,
    val solarNoon: String? = null,
    val solarMidnight: String? = null,
    val moonrise: String? = null,
    val moonset: String? = null,
    val moonPhase: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1DayPart(
    val condition: QWeatherV1Condition? = null,
    val temperatureMax: QWeatherV1Metric? = null,
    val temperatureMin: QWeatherV1Metric? = null,
    val wind: QWeatherV1Wind? = null,
    val windGustMax: QWeatherV1Metric? = null,
    val precipitation: QWeatherV1Precipitation? = null,
    val cloudCover: Double? = null,
    val humidity: Double? = null
)

// ============ v1 通用子模型 ============

@JsonClass(generateAdapter = true)
data class QWeatherV1Metadata(
    val tag: String? = null,
    val attributions: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Condition(
    val text: String? = null,
    val code: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Metric(
    val value: Double? = null,
    val unit: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Wind(
    val direction: QWeatherV1WindDirection? = null,
    val speed: QWeatherV1WindSpeed? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1WindDirection(
    val degree: Double? = null,
    val compass: String? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1WindSpeed(
    val value: Double? = null,
    val unit: String? = null,
    val scale: Double? = null
)

@JsonClass(generateAdapter = true)
data class QWeatherV1Precipitation(
    val amount: QWeatherV1Metric? = null,
    val intensity: QWeatherV1Metric? = null,
    val probability: Double? = null,
    val type: String? = null
)
