package com.ridgecast.weather.data.remote

import android.os.SystemClock
import com.ridgecast.weather.model.WeatherResponse
import com.ridgecast.weather.util.FileLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气网络数据源。
 *
 * 封装 WeatherApiService 的调用，仅负责网络请求。
 * WeatherRepository 通过此类获取网络数据，自身仅负责 Room 缓存。
 *
 * 和风天气的预警数据已内置于 QWeatherApiService 的主请求中，
 * 不再需要独立的预警 API 调用。
 */
@Singleton
class WeatherRemoteDataSource @Inject constructor(
    private val api: WeatherApiService
) {

    companion object {
        private const val TAG = "WeatherRemoteDS"
    }

    private fun weatherI(message: String) = FileLogger.weatherI(TAG, message)
    private fun weatherW(message: String) = FileLogger.weatherW(TAG, message)
    private fun weatherE(message: String, throwable: Throwable? = null) {
        if (throwable == null) FileLogger.weatherE(TAG, message) else FileLogger.weatherE(TAG, message, throwable)
    }

    private fun elapsedSince(startMs: Long): Long = SystemClock.elapsedRealtime() - startMs

    /**
     * 从网络获取天气数据（含预警、空气质量、分钟降水）。
     *
     * 和风天气将所有数据在一次 getWeather 调用中合并返回。
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @param includeYesterday 是否裁剪掉当前小时之前的逐小时数据（写入 Room 前生效）
     * @return 天气数据或错误
     */
    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean = false
    ): Result<WeatherResponse> {
        val totalStartMs = SystemClock.elapsedRealtime()
        weatherI("remote_get_weather_start: lon=$longitude, lat=$latitude, includeYesterday=$includeYesterday")
        return try {
            // v4.1.3 修正：逐小时请求长度**不能**由 includeYesterday 决定。
            // includeYesterday 只负责「是否裁掉当前小时之前的数据」，而唯一生产调用点
            // (WeatherSyncManager) 恒传 true，若据此推导请求长度，240h 会永久失效。
            // 回归 72h（3 天）：渲染端直接全量绘制，避免窗口化/快速滑动占位导致的闪烁。
            // 主界面与 Widget 共用同一份 Room 缓存，统一按 72h 请求。
            val hourlySteps = 72
            weatherI("primary_weather_start: lon=$longitude, lat=$latitude, alert=true, hourlySteps=$hourlySteps")
            val response = api.getWeather(
                longitude = longitude,
                latitude = latitude,
                alert = true,
                hourlySteps = hourlySteps
            )
            weatherI("primary_weather_done: elapsed=${elapsedSince(totalStartMs)}ms, status=${response.status}, serverTime=${response.server_time}, tzshift=${response.tzshift}")
            if (response.status != "ok") {
                weatherW("primary_weather_bad_status: status=${response.status}, total=${elapsedSince(totalStartMs)}ms")
                return Result.failure(Exception("API error: ${response.status}"))
            }

            weatherI("remote_get_weather_success: total=${elapsedSince(totalStartMs)}ms, alertStatus=${response.result?.alert?.status}, alertCount=${response.result?.alert?.content?.size ?: 0}")
            Result.success(response)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            weatherE("remote_get_weather_failed: total=${elapsedSince(totalStartMs)}ms, type=${e.javaClass.simpleName}, message=${e.message}", e)
            Result.failure(e)
        }
    }
}
