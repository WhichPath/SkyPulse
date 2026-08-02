package com.skypulse.weather.data.remote.qweather

import android.os.SystemClock
import com.skypulse.weather.data.remote.WeatherApiService
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气 API 服务实现。
 *
 * 实现 WeatherApiService 接口，编排多个和风端点调用，
 * 将结果映射为现有的 WeatherResponse（彩云格式）模型。
 *
 * 一次刷新发起 6 个并行请求：
 * 1. 实况天气 /v7/weather/now
 * 2. 逐日预报 /weather/v1/daily/{lat}/{lon}（v1，含 uvIndexMax）
 * 3. 逐小时预报 /weather/v1/hourly/{lat}/{lon}（v1，含真实逐小时 uvIndex）
 * 4. 分钟级降水 /v7/minutely/5m
 * 5. 天气预警 /weatheralert/v1/current/{lat}/{lon}
 * 6. 空气质量 /airquality/v1/current/{lat}/{lon}
 *
 * 实况、逐日、逐小时为核心请求（失败则整体失败）；
 * 分钟降水、预警、空气质量为非核心请求（失败不影响主天气数据）。
 */
@Singleton
class QWeatherApiService @Inject constructor(
    private val api: QWeatherApi
) : WeatherApiService {

    companion object {
        private const val TAG = "QWeatherApiService"
    }

    /**
     * 安全执行网络请求，不吞掉 CancellationException。
     * runCatching 会捕获所有 Throwable 包括 CancellationException，破坏结构化并发。
     */
    private suspend fun <T> safeApiCall(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.w(TAG, "API call failed: ${e.message}")
            null
        }
    }

    override suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        span: Int,
        alert: Boolean,
        dailyStart: Int?,
        hourlySteps: Int,
        lang: String
    ): WeatherResponse = coroutineScope {
        val startMs = SystemClock.elapsedRealtime()
        val location = "$longitude,$latitude" // 和风 v7 格式：经度,纬度（now 和 minutely 使用）

        // 核心请求（并行）
        val nowDeferred = async { safeApiCall { api.getWeatherNow(location) } }
        val dailyDeferred = async { safeApiCall { api.getWeatherDailyV1(latitude, longitude, days = 10) } }
        val hourlyDeferred = async { safeApiCall { api.getWeatherHourlyV1(latitude, longitude, hours = if (hourlySteps > 24) 72 else 24) } }

        // 非核心请求（并行，失败不影响主天气）
        val minutelyDeferred = async { safeApiCall { api.getMinutely(location) } }
        val warningDeferred = async {
            if (alert) safeApiCall { api.getWarning(latitude, longitude) } else null
        }
        val airDeferred = async { safeApiCall { api.getAirNow(latitude, longitude) } }

        val now = nowDeferred.await()
        val daily = dailyDeferred.await()
        val hourly = hourlyDeferred.await()
        val minutely = minutelyDeferred.await()
        val warning = warningDeferred.await()
        val air = airDeferred.await()

        // 诊断日志：记录各端点响应码
        FileLogger.i(TAG, "api_response_codes: now=${now?.code}, daily=${daily?.days?.size ?: -1}, hourly=${hourly?.hours?.size ?: -1}, minutely=${minutely?.code}, warning=${warning?.alerts?.size ?: -1}, air=${air?.indexes?.size ?: -1}")

        // 核心数据缺失则抛异常
        if (now?.now == null || daily?.days == null || hourly?.hours == null) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            FileLogger.e(TAG, "core weather data missing: now=${now?.now != null}, daily=${daily?.days != null}, hourly=${hourly?.hours != null}, elapsed=${elapsed}ms")
            throw Exception("QWeather core data incomplete")
        }

        val response = QWeatherMapper.mapToWeatherResponse(
            now = now,
            daily = daily,
            hourly = hourly,
            minutely = minutely,
            warning = warning,
            air = air,
            longitude = longitude,
            latitude = latitude
        )

        val elapsed = SystemClock.elapsedRealtime() - startMs
        FileLogger.i(TAG, "weather fetched: elapsed=${elapsed}ms, skycon=${response.result?.realtime?.skycon}, temp=${response.result?.realtime?.temperature}, alerts=${response.result?.alert?.content?.size ?: 0}")

        response
    }
}
