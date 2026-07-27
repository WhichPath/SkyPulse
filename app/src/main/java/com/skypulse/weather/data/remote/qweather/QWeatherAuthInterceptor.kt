package com.skypulse.weather.data.remote.qweather

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.util.FileLogger
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气鉴权拦截器。
 *
 * 为每个请求自动生成并附加 JWT（Ed25519 签名）到 Authorization: Bearer 头。
 * JWT 缓存 14 分钟后在接近过期时自动刷新。
 */
@Singleton
class QWeatherAuthInterceptor @Inject constructor(
    private val jwtGenerator: QWeatherJwtGenerator
) : Interceptor {

    companion object {
        private const val TAG = "QWeatherAuth"
        private const val TOKEN_TTL_MS = 900_000L // 15 分钟
        private const val REFRESH_BUFFER_MS = 60_000L // 过期前 60 秒刷新
    }

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenGeneratedAt: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = getValidToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            FileLogger.w(TAG, "No JWT available, sending request without auth header")
            chain.request()
        }
        return chain.proceed(request)
    }

    @Synchronized
    private fun getValidToken(): String? {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && now - tokenGeneratedAt < TOKEN_TTL_MS - REFRESH_BUFFER_MS) {
            return cached
        }

        // 生成新 JWT
        val projectId = BuildConfig.QWEATHER_PROJECT_ID
        val keyId = BuildConfig.QWEATHER_KEY_ID
        val privateKey = BuildConfig.QWEATHER_PRIVATE_KEY

        if (projectId.isBlank() || keyId.isBlank() || privateKey.isBlank()) {
            FileLogger.w(TAG, "QWeather credentials not configured (projectId/keyId/privateKey empty)")
            return cached // 返回旧 token 兜底，或 null
        }

        val newToken = jwtGenerator.generateToken(projectId, keyId, privateKey)
        return if (newToken != null) {
            cachedToken = newToken
            tokenGeneratedAt = now
            newToken
        } else {
            FileLogger.e(TAG, "JWT generation returned null, using stale token if available")
            cached
        }
    }
}
