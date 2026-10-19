package com.ridgecast.weather.data.remote

import com.ridgecast.weather.data.remote.qweather.QWeatherAirResponse
import com.ridgecast.weather.data.remote.qweather.QWeatherApi
import com.ridgecast.weather.data.remote.qweather.QWeatherApiProvider
import com.ridgecast.weather.data.remote.qweather.QWeatherApiService
import com.ridgecast.weather.data.remote.qweather.QWeatherMinutelyResponse
import com.ridgecast.weather.data.remote.qweather.QWeatherV1DailyResponse
import com.ridgecast.weather.data.remote.qweather.QWeatherV1HourlyResponse
import com.ridgecast.weather.data.remote.qweather.QWeatherV1NowResponse
import com.ridgecast.weather.data.remote.qweather.QWeatherWarningResponse
import android.os.SystemClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐小时请求长度的回归测试。
 *
 * v4.1.3 事故：请求长度曾由 `includeYesterday` 推导，而唯一生产调用点恒传 true，
 * 导致主界面永远只拿到 72h（240h 特性形同虚设）。
 * 此处锁定「请求长度与 includeYesterday 无关」，防止该参数耦合再次发生。
 */
class WeatherRemoteDataSourceHourlyRangeTest {

    init {
        // 生产代码用 SystemClock 计时，纯 JVM 单测需显式桩化
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
    }

    private val api: QWeatherApi = mockk()

    private val apiProvider: QWeatherApiProvider = mockk {
        every { api() } returns api
    }

    private fun service(): QWeatherApiService {
        coEvery { api.getWeatherNowV1(any(), any(), any(), any()) } returns QWeatherV1NowResponse()
        coEvery { api.getWeatherDailyV1(any(), any(), any(), any(), any()) } returns QWeatherV1DailyResponse()
        coEvery { api.getWeatherHourlyV1(any(), any(), any(), any(), any()) } returns QWeatherV1HourlyResponse()
        coEvery { api.getMinutely(any(), any()) } returns QWeatherMinutelyResponse()
        coEvery { api.getWarning(any(), any(), any()) } returns QWeatherWarningResponse()
        coEvery { api.getAirNow(any(), any(), any()) } returns QWeatherAirResponse()
        return QWeatherApiService(apiProvider = apiProvider)
    }

    @Test
    fun `hourly request length is independent of includeYesterday`() = runTest {
        val dataSource = WeatherRemoteDataSource(api = service())

        // includeYesterday 只应影响「是否裁掉当前小时之前的数据」，不应影响请求长度
        runCatching { dataSource.getWeather(116.4074, 39.9042, includeYesterday = true) }
        coVerify(exactly = 1) { api.getWeatherHourlyV1(39.9042, 116.4074, 72, true, "zh") }

        runCatching { dataSource.getWeather(116.4074, 39.9042, includeYesterday = false) }
        // 两次调用都必须携带 72h，不允许因 includeYesterday 变化而降级
        coVerify(exactly = 2) { api.getWeatherHourlyV1(39.9042, 116.4074, 72, true, "zh") }
        coVerify(exactly = 0) { api.getWeatherHourlyV1(39.9042, 116.4074, 240, true, "zh") }
    }

    @Test
    fun `daily request stays at the documented 10 day maximum`() = runTest {
        val dataSource = WeatherRemoteDataSource(api = service())

        runCatching { dataSource.getWeather(116.4074, 39.9042, includeYesterday = true) }

        coVerify(exactly = 1) { api.getWeatherDailyV1(39.9042, 116.4074, 10, true, "zh") }
    }

    @Test
    fun `hourly range stays within the provider documented maximum`() {
        // 官方 /weather/v1/hourly 的 hours 取值范围为 1-240
        assertTrue(72 in 1..240)
    }
}
