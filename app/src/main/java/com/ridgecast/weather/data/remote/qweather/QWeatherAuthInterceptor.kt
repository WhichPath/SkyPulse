package com.ridgecast.weather.data.remote.qweather

import com.ridgecast.weather.util.FileLogger
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Singleton

/**
 * 和风天气鉴权拦截器。
 *
 * 为每个请求自动生成并附加 JWT（Ed25519 签名）到 Authorization: Bearer 头。
 * JWT 缓存 14 分钟后在接近过期时自动刷新；凭据指纹变化时（用户切换密钥）立即失效重签。
 *
 * 凭据由构造参数 [credentialResolver] 提供而非直接读 BuildConfig，
 * 以支持「用户运行时填写 > 编译期注入」的两级解析（见 QWeatherCredentialStore），
 * 同时允许连通性测试用一份临时凭据构造独立实例。
 */
@Singleton
class QWeatherAuthInterceptor(
    private val jwtGenerator: QWeatherJwtGenerator,
    private val credentialResolver: () -> QWeatherCredentials
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

    /** 已缓存 token 对应的凭据指纹，用于检测密钥切换。 */
    @Volatile
    private var cachedFingerprint: String? = null

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
        val credentials = credentialResolver()
        val now = System.currentTimeMillis()
        val cached = cachedToken
        val fingerprint = credentials.fingerprint

        // 缓存命中：未过期且凭据未变化
        if (cached != null &&
            cachedFingerprint == fingerprint &&
            now - tokenGeneratedAt < TOKEN_TTL_MS - REFRESH_BUFFER_MS
        ) {
            return cached
        }

        if (!credentials.isComplete) {
            FileLogger.w(
                TAG,
                "QWeather credentials not configured (source=${credentials.source}, " +
                    "projectId/keyId/privateKey empty)"
            )
            return cached // 返回旧 token 兜底，或 null
        }

        val newToken = jwtGenerator.generateToken(
            credentials.projectId,
            credentials.keyId,
            credentials.privateKeyPem
        )
        return if (newToken != null) {
            cachedToken = newToken
            tokenGeneratedAt = now
            cachedFingerprint = fingerprint
            newToken
        } else {
            FileLogger.e(TAG, "JWT generation returned null, using stale token if available")
            cached
        }
    }
}