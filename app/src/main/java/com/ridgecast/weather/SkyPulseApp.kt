package com.ridgecast.weather

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ridgecast.weather.notification.WeatherNotificationScheduler
import com.ridgecast.weather.util.FileLogger
import com.ridgecast.weather.widget.WeatherWidgetProvider
import com.ridgecast.weather.widget.WeatherWidgetMediumProvider
import com.ridgecast.weather.widget.WeatherWidget4x2Provider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SkyPulseApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        FileLogger.initCrashHandler()

        WeatherNotificationScheduler.scheduleIfNeeded(this)
        WeatherWidgetProvider.enqueueWorker(this)
        WeatherWidgetMediumProvider.enqueueWorker(this)
        WeatherWidget4x2Provider.enqueueWorker(this)
    }
}
