package com.ridgecast.weather.data.remote.qweather

import com.ridgecast.weather.util.FileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** 连通性测试结果。 */
sealed interface QWeatherConnectionResult {
    /** 测试成功，[detail] 为可展示的实况摘要。 */
    data class Success(val detail: String) : QWeatherConnectionResult

    /** 测试失败，[message] 为面向用户的中文原因。 */
    data class Failure(val message: String) : QWeatherConnectionResult
}

/**
 * 和风天气凭据连通性测试器。
 *
 * 使用候选凭据构建一次性 API 实例并请求北京实况天气：
 * - 覆盖了真实链路的全部关键环节（Host 可达、JWT 签名正确、Project ID / Key ID 匹配、公钥已上传）；
 * - 不写入任何缓存，失败也不会污染正式请求；
 * - 在 IO 线程执行，调用方直接用于按钮回调即可。
 */
@Singleton
class QWeatherCredentialTester @Inject constructor(
    private val apiProvider: QWeatherApiProvider
) {

    companion object {
        private const val TAG = "QWeatherCredentialTester"
        /** 测试坐标：北京天安门，用于验证凭据而非验证某地天气。 */
        private const val TEST_LATITUDE = 39.9042
        private const val TEST_LONGITUDE = 116.4074
    }

    suspend fun test(credentials: QWeatherCredentials): QWeatherConnectionResult =
        withContext(Dispatchers.IO) {
            val hostError = validateApiHost(credentials.apiHost)
            when {
                hostError != null ->
                    QWeatherConnectionResult.Failure(hostError)

                credentials.apiHost.trim().startsWith("http://") ->
                    QWeatherConnectionResult.Failure("API Host 请使用 https:// 明文协议")

                !credentials.isComplete ->
                    QWeatherConnectionResult.Failure("凭据不完整，请填写 Project ID、Key ID 与私钥")

                !credentials.hasPemShape ->
                    QWeatherConnectionResult.Failure("私钥格式不正确，需要包含 BEGIN / END 标记的完整 PEM 文本")

                else -> request(credentials)
            }
        }

    /** API Host 校验，返回面向用户的中文提示；合法时返回 null。 */
    private fun validateApiHost(raw: String): String? {
        val host = raw.trim()
        return when {
            host.isEmpty() -> "请填写 API Host（和风控制台-设置页）"
            host.contains("REPLACE_WITH_YOUR_API_HOST") ->
                "API Host 仍是占位默认值，请填入控制台-设置页的专属 Host"
            host.contains("qweather.com") && !host.contains("qweatherapi.com") ->
                "api.qweather.com / devapi.qweather.com 等公共域名已停服，请改用控制台-设置页的专属 API Host"
            host.contains(".qweatherapi.com") -> null
            else -> "API Host 格式有误，应为控制台-设置页的专属域名（形如 xxxxxx.xx.qweatherapi.com）"
        }
    }

    private suspend fun request(credentials: QWeatherCredentials): QWeatherConnectionResult {
        return try {
            val api = apiProvider.createEphemeralApi(credentials)
            val response = api.getWeatherNowV1(TEST_LATITUDE, TEST_LONGITUDE)
            val condition = response.condition
            val temperature = response.temperature?.value

            if (condition == null && temperature == null) {
                QWeatherConnectionResult.Failure("鉴权通过但未返回实况数据，请确认账号已开通天气服务")
            } else {
                val text = condition?.text ?: "未知天气"
                val tempText = temperature?.let { " ${it.toInt()}℃" }.orEmpty()
                QWeatherConnectionResult.Success("连接成功 · 北京 $text$tempText")
            }
        } catch (e: CancellationException) {
            // 不吞掉协程取消，否则页面退出后仍会回写状态
            throw e
        } catch (e: Exception) {
            FileLogger.w(TAG, "credential test failed: ${e.message}")
            QWeatherConnectionResult.Failure(describe(e))
        }
    }

    private fun describe(error: Throwable): String = when (error) {
        is HttpException -> {
            val body = try {
                error.response()?.errorBody()?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            when (error.code()) {
                401 -> "鉴权失败（401）：Project ID、Key ID 或私钥与控制台公钥不匹配"
                403 -> "鉴权被拒（403）：该账号无权访问此接口或已欠费停用"
                404 -> "接口不存在（404）：API Host 填写有误"
                else -> "请求失败（HTTP ${error.code()}）${body.take(120)}"
            }
        }

        is java.net.UnknownHostException -> "域名解析失败：请检查 API Host 拼写与网络（控制台-设置页的专属 Host）"
        is java.net.SocketTimeoutException -> "连接超时：请检查网络或 API Host"
        is javax.net.ssl.SSLException -> "TLS 握手失败：API Host 必须是有效的 https 地址"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "未知错误，请稍后重试"
    }
}