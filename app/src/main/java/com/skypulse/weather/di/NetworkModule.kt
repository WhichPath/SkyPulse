package com.skypulse.weather.di

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.remote.GithubApi
import com.skypulse.weather.data.remote.WeatherApiService
import com.skypulse.weather.data.remote.qweather.QWeatherApi
import com.skypulse.weather.data.remote.qweather.QWeatherAuthInterceptor
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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
     * 和风天气专用 Retrofit 实例。
     *
     * 在共享 OkHttpClient 基础上追加 QWeatherAuthInterceptor（JWT 鉴权）。
     * Base URL 为用户在 local.properties 中配置的项目 API Host。
     */
    @Provides
    @Singleton
    fun provideQWeatherRetrofit(
        client: OkHttpClient,
        authInterceptor: QWeatherAuthInterceptor,
        moshi: Moshi
    ): Retrofit {
        val qweatherClient = client.newBuilder()
            .addInterceptor(authInterceptor)
            .build()

        var baseUrl = BuildConfig.QWEATHER_API_HOST
        if (!baseUrl.endsWith("/")) baseUrl += "/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(qweatherClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideQWeatherApi(retrofit: Retrofit): QWeatherApi =
        retrofit.create(QWeatherApi::class.java)

    @Provides
    @Singleton
    fun provideGithubApi(client: OkHttpClient, moshi: Moshi): GithubApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)
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
        impl: com.skypulse.weather.data.remote.qweather.QWeatherApiService
    ): WeatherApiService
}
