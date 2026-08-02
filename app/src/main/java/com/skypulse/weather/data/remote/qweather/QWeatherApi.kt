package com.skypulse.weather.data.remote.qweather

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 和风天气 Retrofit API 接口。
 *
 * 包含三类端点，URL 路径风格不同：
 * - v7 天气端点：location 为查询参数，格式 "经度,纬度"（lon,lat）
 * - v1 预警/空气端点：经纬度为路径参数，格式 {lat}/{lon}（纬度在前）
 * - v2 GeoAPI：location 为查询参数，可为城市名或 "经度,纬度"
 *
 * 所有请求由 QWeatherAuthInterceptor 自动附加 JWT 鉴权头。
 */
interface QWeatherApi {

    // ============ v7 天气预报 ============

    /** 实况天气 */
    @GET("v7/weather/now")
    suspend fun getWeatherNow(
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QWeatherNowResponse

    /** 逐日预报，days 可选 "3d"/"7d"/"10d"/"15d"/"30d" */
    @GET("v7/weather/{days}")
    suspend fun getWeatherDaily(
        @Path("days") days: String,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QWeatherDailyResponse

    /** 逐小时预报，hours 可选 "24h"/"72h"/"168h" */
    @GET("v7/weather/{hours}")
    suspend fun getWeatherHourly(
        @Path("hours") hours: String,
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QWeatherHourlyResponse

    /** 分钟级降水预报（未来 2 小时） */
    @GET("v7/minutely/5m")
    suspend fun getMinutely(
        @Query("location") location: String,
        @Query("lang") lang: String = "zh"
    ): QWeatherMinutelyResponse

    // ============ v1 天气预警 ============

    /** 当前天气预警（路径参数：纬度/经度） */
    @GET("weatheralert/v1/current/{lat}/{lon}")
    suspend fun getWarning(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("lang") lang: String = "zh"
    ): QWeatherWarningResponse

    // ============ v1 空气质量 ============

    /** 当前空气质量（路径参数：纬度/经度） */
    @GET("airquality/v1/current/{lat}/{lon}")
    suspend fun getAirNow(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("lang") lang: String = "zh"
    ): QWeatherAirResponse

    // ============ v2 GeoAPI ============

    /** 城市搜索，location 可为城市名或 "经度,纬度" */
    @GET("geo/v2/city/lookup")
    suspend fun searchCity(
        @Query("location") query: String,
        @Query("number") number: Int = 10,
        @Query("lang") lang: String = "zh"
    ): QWeatherGeoResponse

    // ============ v1 天气预报 (新版 API) ============

    /** 逐小时预报（路径参数：纬度/经度，含真实逐小时 uvIndex） */
    @GET("weather/v1/hourly/{lat}/{lon}")
    suspend fun getWeatherHourlyV1(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("hours") hours: Int = 24,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh"
    ): QWeatherV1HourlyResponse

    /** 逐日预报（路径参数：纬度/经度，含 uvIndexMax 和白天/夜间分段，最多 10 天） */
    @GET("weather/v1/daily/{lat}/{lon}")
    suspend fun getWeatherDailyV1(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("days") days: Int = 10,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh"
    ): QWeatherV1DailyResponse
}
