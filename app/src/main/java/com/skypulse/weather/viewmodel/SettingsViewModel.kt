package com.skypulse.weather.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.skypulse.weather.data.QWeatherCredentialStore
import com.skypulse.weather.data.SettingsRepository
import com.skypulse.weather.data.ThemeMode
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.data.remote.qweather.QWeatherConnectionResult
import com.skypulse.weather.data.remote.qweather.QWeatherCredentialTester
import com.skypulse.weather.data.remote.qweather.QWeatherCredentials
import com.skypulse.weather.notification.WeatherNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val credentialStore: QWeatherCredentialStore,
    private val credentialTester: QWeatherCredentialTester
) : ViewModel() {

    val settings: StateFlow<WeatherSettings> = settingsRepository.settings

    /** 当前生效的和风天气凭据（用户填写优先，其次编译内置）。 */
    val credentials: StateFlow<QWeatherCredentials> = credentialStore.resolved

    /** 安装包是否编译进了可用凭据（无密钥包体为 false）。 */
    val hasBuildInjectedCredentials: Boolean
        get() = credentialStore.buildInjected.isComplete

    fun setRainAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setRainAlert(enabled)
    }

    fun setWarningAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setWarningAlert(enabled)
    }

    fun setTempChangeAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setTempChangeAlert(enabled)
    }

    fun setWindAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setWindAlert(enabled)
    }

    fun setTyphoonAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setTyphoonAlert(enabled)
    }

    fun setShowHourlyAqi(enabled: Boolean) = settingsRepository.setShowHourlyAqi(enabled)
    fun setShowHourlyUv(enabled: Boolean) = settingsRepository.setShowHourlyUv(enabled)
    fun setShowHourlyWind(enabled: Boolean) = settingsRepository.setShowHourlyWind(enabled)
    fun setShowHourlyWindGust(enabled: Boolean) = settingsRepository.setShowHourlyWindGust(enabled)
    fun setShowCardDetail(enabled: Boolean) = settingsRepository.setShowCardDetail(enabled)
    fun setShowCardSunriseSunset(enabled: Boolean) = settingsRepository.setShowCardSunriseSunset(enabled)
    fun setShowCardMinutely(enabled: Boolean) = settingsRepository.setShowCardMinutely(enabled)
    fun setShowCardTyphoon(enabled: Boolean) = settingsRepository.setShowCardTyphoon(enabled)
    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun saveQWeatherCredentials(
        projectId: String,
        keyId: String,
        privateKeyPem: String,
        apiHost: String
    ) = credentialStore.save(projectId, keyId, privateKeyPem, apiHost)

    fun clearQWeatherCredentials() = credentialStore.clear()

    /** 用候选凭据做一次连通性验证，不写入任何存储。 */
    suspend fun testQWeatherCredentials(
        credentials: QWeatherCredentials
    ): QWeatherConnectionResult = credentialTester.test(credentials)

    private fun updateAlertSetting(update: () -> Unit) {
        update()
        WeatherNotificationScheduler.scheduleIfNeeded(appContext)
    }
}