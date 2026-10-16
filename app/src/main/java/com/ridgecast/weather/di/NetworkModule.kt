package com.ridgecast.weather.di

import com.ridgecast.weather.BuildConfig
import com.ridgecast.weather.data.QWeatherCredentialStore
import com.ridgecast.weather.data.remote.WeatherApiService
import com.ridgecast.weather.data.remote.qweather.QWeatherApiService
import com.ridgecast.weather.data.remote.qweather.QWeatherAuthInterceptor
import com.ridgecast.weather.data.remote.qweather.QWeatherJwtGenerator
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "weather/${BuildConfig.VERSION_NAME} (${android.os.Build.MANUFACTURER}:${android.os.Build.MODEL}; android/${android.os.Build.VERSION.RELEASE})")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 和风天气鉴权拦截器。
     *
     * 凭据解析采用动态回调，运行时优先级为「设置页用户填写 > 编译期 BuildConfig 注入」，
     * 因此用户在设置页更换密钥后无需重启进程即可生效。
     */
    @Provides
    @Singleton
    fun provideQWeatherAuthInterceptor(
        jwtGenerator: QWeatherJwtGenerator,
        credentialStore: QWeatherCredentialStore
    ): QWeatherAuthInterceptor = QWeatherAuthInterceptor(jwtGenerator) {
        credentialStore.resolveCurrent()
    }
}

/**
 * 将 WeatherApiService 接口绑定到 QWeatherApiService 实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {

    @Binds
    @Singleton
    abstract fun bindWeatherApiService(
        impl: QWeatherApiService
    ): WeatherApiService
}