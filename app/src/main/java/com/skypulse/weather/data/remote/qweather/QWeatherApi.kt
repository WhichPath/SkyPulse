package com.skypulse.weather.data.remote.qweather

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 和风天气 Retrofit API 接口。
 *
 * 端点 URL 路径风格：
 * - v1 天气/预警/空气端点：经纬度为路径参数，格式 {lat}/{lon}（纬度在前）
 * - v7 分钟降水端点：location 为查询参数，格式 "经度,纬度"（lon,lat），和风无 v1 版本
 * - v2 GeoAPI：location 为查询参数，可为城市名或 "经度,纬度"
 *
 * 所有请求由 QWeatherAuthInterceptor 自动附加 JWT 鉴权头。
 */
interface QWeatherApi {

    // ============ v7 分钟级降水（和风无 v1 版本） ============

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

    /** 实况天气（路径参数：纬度/经度，含真实 uvIndex 和 windGust） */
    @GET("weather/v1/current/{lat}/{lon}")
    suspend fun getWeatherNowV1(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh"
    ): QWeatherV1NowResponse

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
