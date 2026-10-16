package com.ridgecast.weather.data.remote.qweather

import com.ridgecast.weather.data.QWeatherCredentialStore
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气 API 实例提供器。
 *
 * Retrofit 的 baseUrl 在构建后不可变，而 API Host 允许用户在设置页自定义，
 * 因此这里按 Host 缓存 Retrofit/API 实例：Host 不变时复用同一实例，
 * Host 变化时重建并替换缓存。
 *
 * 鉴权拦截器持有的是「动态解析」的凭据回调，因此即使 Host 未变、仅密钥被替换，
 * 拦截器内部也会因凭据指纹变化而立即重新签名，无需重启进程。
 */
@Singleton
class QWeatherApiProvider @Inject constructor(
    private val baseClient: OkHttpClient,
    moshi: Moshi,
    private val jwtGenerator: QWeatherJwtGenerator,
    private val authInterceptor: QWeatherAuthInterceptor,
    private val credentialStore: QWeatherCredentialStore
) {
    private val moshiConverterFactory = MoshiConverterFactory.create(moshi)

    @Volatile
    private var cachedHost: String? = null

    @Volatile
    private var cachedApi: QWeatherApi? = null

    /** 当前生效凭据对应的 API 实例（按 API Host 缓存）。 */
    fun api(): QWeatherApi {
        val host = credentialStore.resolveCurrent().normalizedApiHost
        val cached = cachedApi
        if (cached != null && cachedHost == host) return cached
        return synchronized(this) {
            val current = cachedApi
            if (current != null && cachedHost == host) {
                current
            } else {
                build(host = host, interceptor = authInterceptor).also {
                    cachedHost = host
                    cachedApi = it
                }
            }
        }
    }

    /**
     * 用一份「候选凭据」构建一次性 API 实例，供设置页连通性测试使用。
     * 不触碰 [cachedApi]，因此测试失败不会影响正式请求链路。
     */
    fun createEphemeralApi(credentials: QWeatherCredentials): QWeatherApi = build(
        host = credentials.normalizedApiHost,
        interceptor = QWeatherAuthInterceptor(jwtGenerator) { credentials }
    )

    private fun build(host: String, interceptor: Interceptor): QWeatherApi {
        val client = baseClient.newBuilder()
            .addInterceptor(interceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl(QWeatherCredentials.normalizeApiHost(host))
            .client(client)
            .addConverterFactory(moshiConverterFactory)
            .build()
            .create(QWeatherApi::class.java)
    }
}