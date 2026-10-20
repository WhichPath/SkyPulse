package com.ridgecast.weather.data.remote.qweather

import android.os.SystemClock
import com.ridgecast.weather.data.remote.WeatherApiService
import com.ridgecast.weather.model.WeatherResponse
import com.ridgecast.weather.util.FileLogger
import com.ridgecast.weather.util.WeatherDataValidator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气 API 服务实现。
 *
 * 实现 WeatherApiService 接口，编排多个和风端点调用，
 * 将结果映射为现有的 WeatherResponse（彩云格式）模型。
 *
 * 一次刷新发起 7 个并行请求：
 * 1. 实况天气 /weather/v1/current/{lat}/{lon}（v1，含 uvIndex）
 * 2. 逐日预报 /weather/v1/daily/{lat}/{lon}（v1，含 uvIndexMax）
 * 3. 逐小时预报 /weather/v1/hourly/{lat}/{lon}（v1，含真实逐小时 uvIndex）
 * 4. 分钟级降水 /v7/minutely/5m（v7，和风无 v1 版本）
 * 5. 天气预警 /weatheralert/v1/current/{lat}/{lon}
 * 6. 空气质量 /airquality/v1/current/{lat}/{lon}
 * 7. 逐小时空气质量 /airquality/v1/hourly/{lat}/{lon}（未来 24h 分项污染物）
 *
 * 实况、逐日、逐小时为核心请求（失败则整体失败）；
 * 分钟降水、预警、空气质量、逐小时空气质量为非核心请求（失败不影响主天气数据）。
 */
@Singleton
class QWeatherApiService @Inject constructor(
    private val apiProvider: QWeatherApiProvider
) : WeatherApiService {

    companion object {
        private const val TAG = "QWeatherApiService"

        /** 本地日期 yyyyMMdd（天端点按城市当地日期查询）。 */
        private fun formatLocalDate(): String {
            val now = Calendar.getInstance()
            return String.format(
                Locale.US, "%04d%02d%02d",
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)
            )
        }

        /** 本地时刻 HHmm。 */
        private fun formatLocalTime(): String {
            val now = Calendar.getInstance()
            return String.format(
                Locale.US, "%02d%02d",
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE)
            )
        }

        /**
         * 本地时区偏移（±HHmm）。
         *
         * 天文端点必须带时区；此处用设备时区近似城市时区（绝大多数用户一致），
         * 若不一致也只是让接口光源略微偏差，本地 NOAA/Meeus 算法仍是最终兜底。
         */
        private fun formatLocalTimezoneOffset(): String {
            val now = Calendar.getInstance()
            val offsetMinutes = now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET)
            val sign = if (offsetMinutes < 0) "-" else "+"
            val abs = kotlin.math.abs(offsetMinutes)
            return String.format(Locale.US, "%s%02d%02d", sign, abs / 60, abs % 60)
        }
    }

    /**
     * 每次刷新都重新解析 API 实例：用户若在设置页切换了 API Host，
     * 这里会拿到指向新 Host 的实例，无需重启进程。
     */
    private val api: QWeatherApi
        get() = apiProvider.api()

    /**
     * 安全执行网络请求，不吞掉 CancellationException。
     * runCatching 会捕获所有 Throwable 包括 CancellationException，破坏结构化并发。
     */
    /**
     * issueChannel 非 null 时，非取消异常会被记录为一个带 source/严重度/用户提示的 DataIssue，
     * 但**本地可兜底项不算用户可见失败**（isLocallyHandled=true 时不记录）。
     * 返回 null 表示该端点失败；调用方自己决定是否抛异常/静默。
     */
    private fun classifyQWeatherFailure(endpoint: String, e: Exception): Pair<String, String> {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("401") || (e is retrofit2.HttpException && e.code() == 401) ->
                "error" to "鉴权失败（401）：${humanSource(endpoint)}凭据与控制台公钥不匹配"
            msg.contains("403") || (e is retrofit2.HttpException && e.code() == 403) ->
                "error" to "鉴权被拒（403）：该账号无权访问此接口或已欠费停用"
            msg.contains("404") || (e is retrofit2.HttpException && e.code() == 404) ->
                "error" to "接口不存在（404）：API Host 或端点配置有误"
            e is java.net.UnknownHostException ->
                "error" to "${humanSource(endpoint)}的域名無法解析，一般是因为 API Host 无 DNS 记录，请核对控制台-设置页的专属 Host"
            e is java.net.SocketTimeoutException ->
                "warning" to "连接超时：网络较慢或 API Host 不可达"
            else ->
                "warning" to "${humanSource(endpoint)}暂时拉取失败（${msg.take(60).ifBlank { "网络/服务异常" }}）"
        }
    }

    private fun humanSource(endpoint: String): String = when (endpoint) {
        "warning" -> "预警"
        "air" -> "空气质量"
        "airHourly" -> "逐小时空气质量"
        "minutely" -> "分钟级降水"
        "current" -> "实况"
        "daily" -> "每日预报"
        "hourly" -> "逐小时预报"
        else -> endpoint
    }

    private suspend fun <T> safeApiCall(
        endpoint: String,
        issueSink: ((issue: com.ridgecast.weather.model.DataIssue) -> Unit)? = null,
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.w(TAG, "$endpoint call failed: ${e.message}")
            val (severity, userMessage) = classifyQWeatherFailure(endpoint, e)
            issueSink?.invoke(
                com.ridgecast.weather.model.DataIssue(
                    code = "fetch_failed_${endpoint}",
                    source = endpoint,
                    severity = severity,
                    message = userMessage,
                    localResolution = false
                )
            )
            null
        }
    }

    override suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        alert: Boolean,
        hourlySteps: Int
    ): WeatherResponse = coroutineScope {
        val startMs = SystemClock.elapsedRealtime()
        val location = "$longitude,$latitude" // 和风 v7 格式：经度,纬度（仅 minutely 使用）

        val issues = java.util.concurrent.ConcurrentLinkedQueue<com.ridgecast.weather.model.DataIssue>()
        val reportIssue: (com.ridgecast.weather.model.DataIssue) -> Unit = { it: com.ridgecast.weather.model.DataIssue ->
            issues.add(it)
        }

        // 核心请求（并行）— 失败会被 safeApiCall 记录为 DataIssue，且 QWeatherMapper 将抛异常
        val nowDeferred = async {
            safeApiCall(endpoint = "current", issueSink = reportIssue) { api.getWeatherNowV1(latitude, longitude) }
        }
        val dailyDeferred = async {
            safeApiCall(endpoint = "daily", issueSink = reportIssue) { api.getWeatherDailyV1(latitude, longitude, days = 10) }
        }
        val hourlyDeferred = async {
            safeApiCall(endpoint = "hourly", issueSink = reportIssue) { api.getWeatherHourlyV1(latitude, longitude, hours = hourlySteps) }
        }

        // 非核心请求（并行，失败不影响主天气）
        val minutelyDeferred = async {
            safeApiCall(endpoint = "minutely", issueSink = reportIssue) { api.getMinutely(location) }
        }
        val warningDeferred = async {
            if (alert) safeApiCall(endpoint = "warning", issueSink = reportIssue) { api.getWarning(latitude, longitude) } else null
        }
        val airDeferred = async {
            safeApiCall(endpoint = "air", issueSink = reportIssue) { api.getAirNow(latitude, longitude) }
        }
        // 逐小时空气质量：用于首页逐时 AQI 行（分项浓度 → 本地 US EPA 口径自算）。
        // **不记 DataIssue**：该端点属于「有则显示、无则不显示」的增强行，
        // 失败时整行缺席即可；报错会让每次刷新都弹提示（用户未开通该 API 时尤其烦人）。
        val airHourlyDeferred = async {
            safeApiCall(endpoint = "airHourly", issueSink = null) { api.getAirHourly(latitude, longitude) }
        }

        // 天文（玻璃高光的物理光源）：非核心增强，且**本地可兜底**，
        // 因此不记 DataIssue（不打扰用户），失败时 UI 用本地算法重算。
        val localDate = formatLocalDate()
        val localTime = formatLocalTime()
        val localTz = formatLocalTimezoneOffset()
        val astronomySolarDeferred = async {
            safeApiCall(endpoint = "astronomy", issueSink = null) {
                api.getSolarElevationAngle(
                    location = location,
                    date = localDate,
                    time = localTime,
                    tz = localTz
                )
            }
        }
        val astronomyMoonDeferred = async {
            safeApiCall(endpoint = "astronomy", issueSink = null) {
                api.getAstronomyMoon(location = location, date = localDate)
            }
        }

        val now = nowDeferred.await()
        val daily = dailyDeferred.await()
        val hourly = hourlyDeferred.await()
        val minutely = minutelyDeferred.await()
        val warning = warningDeferred.await()
        val air = airDeferred.await()
        val airHourly = airHourlyDeferred.await()
        val astronomySolar = astronomySolarDeferred.await()
        val astronomyMoon = astronomyMoonDeferred.await()

        // 诊断日志：记录各端点响应码
        FileLogger.i(TAG, "api_response_codes: now=${now?.condition?.code}, daily=${daily?.days?.size ?: -1}, hourly=${hourly?.hours?.size ?: -1}, minutely=${minutely?.code}, warning=${warning?.alerts?.size ?: -1}, air=${air?.indexes?.size ?: -1}, airHourly=${airHourly?.hours?.size ?: -1}")

        // 核心数据缺失则抛异常（允许上层 decide 是否回退缓存）
        if (now?.condition == null || daily?.days == null || hourly?.hours == null) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            FileLogger.e(TAG, "core weather data missing: now=${now?.condition != null}, daily=${daily?.days != null}, hourly=${hourly?.hours != null}, elapsed=${elapsed}ms")
            throw Exception("QWeather core data incomplete")
        }

        val response = QWeatherMapper.mapToWeatherResponse(
            now = now,
            daily = daily,
            hourly = hourly,
            minutely = minutely,
            warning = warning,
            air = air,
            airHourly = airHourly,
            astronomySolar = astronomySolar,
            astronomyMoon = astronomyMoon,
            longitude = longitude,
            latitude = latitude,
            dataIssues = issues.toList()
        )

        val elapsed = SystemClock.elapsedRealtime() - startMs
        FileLogger.i(TAG, "weather fetched: elapsed=${elapsed}ms, skycon=${response.result?.realtime?.skycon}, temp=${response.result?.realtime?.temperature}, alerts=${response.result?.alert?.content?.size ?: 0}, issues=${response.dataIssues?.size ?: 0}")

        // 数据完整性自检（默认关闭，打开后写文件日志）
        WeatherDataValidator.logIfEnabled("api", response)

        response
    }
}
