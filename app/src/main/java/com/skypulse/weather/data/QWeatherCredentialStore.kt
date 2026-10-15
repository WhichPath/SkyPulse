package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.remote.qweather.QWeatherCredentials
import com.skypulse.weather.data.remote.qweather.QWeatherCredentialSource
import com.skypulse.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气凭据存储（v4.0.2 新增）。
 *
 * 目标：把「密钥环节」与「编译环节」解耦。
 * - 用户在设置页填写的凭据存于本机 SharedPreferences，运行时优先生效；
 * - 编译期经 BuildConfig 注入的凭据（local.properties / CI secrets）作为回退继续保留；
 * - 两者都缺失时，[resolveCurrent] 返回空凭据，鉴权拦截器会跳过 Authorization 头，
 *   App 仍可正常启动并在设置页提示用户补全（不再出现「必须先编译才能用」的硬依赖）。
 *
 * 存储位置为应用私有目录的普通 SharedPreferences，不参与备份上传之外的任何外发；
 * 与编译注入方式相比，构建产物中不再必然携带密钥。
 */
@Singleton
class QWeatherCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _resolved = MutableStateFlow(resolveFromDisk())

    /** 当前生效的凭据（用户优先，其次编译内置）。供设置页展示来源状态。 */
    val resolved: StateFlow<QWeatherCredentials> = _resolved.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _resolved.value = resolveFromDisk()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** 当前是否由用户在设置页提供了凭据。 */
    val isUserProvided: Boolean
        get() = _resolved.value.source == QWeatherCredentialSource.USER

    /** 编译期内置的凭据（未配置时字段为空）。 */
    val buildInjected: QWeatherCredentials
        get() = QWeatherCredentials(
            projectId = BuildConfig.QWEATHER_PROJECT_ID.trim(),
            keyId = BuildConfig.QWEATHER_KEY_ID.trim(),
            privateKeyPem = BuildConfig.QWEATHER_PRIVATE_KEY.trim(),
            apiHost = BuildConfig.QWEATHER_API_HOST.trim()
                .ifEmpty { QWeatherCredentials.DEFAULT_API_HOST },
            source = QWeatherCredentialSource.BUILD
        )

    /**
     * 同步解析当前凭据。OkHttp 拦截器在任意线程调用，必须是非挂起函数。
     * SharedPreferences 读取已在内存缓存，开销可忽略。
     */
    fun resolveCurrent(): QWeatherCredentials = resolveFromDisk()

    /** 保存用户填写的凭据；字段会做 trim 与空串归一化。 */
    fun save(projectId: String, keyId: String, privateKeyPem: String, apiHost: String) {
        val normalizedApiHost = if (apiHost.isBlank()) {
            QWeatherCredentials.DEFAULT_API_HOST
        } else {
            apiHost.trim()
        }
        prefs.edit()
            .putString(KEY_PROJECT_ID, projectId.trim())
            .putString(KEY_KEY_ID, keyId.trim())
            .putString(KEY_PRIVATE_KEY_PEM, privateKeyPem.trim())
            .putString(KEY_API_HOST, normalizedApiHost)
            .apply()
        FileLogger.i(TAG, "user QWeather credentials saved (host=$normalizedApiHost)")
    }

    /** 清除用户凭据，回退到编译内置（若编译也未注入，则为未配置状态）。 */
    fun clear() {
        prefs.edit().clear().apply()
        FileLogger.i(TAG, "user QWeather credentials cleared, falling back to build-time injection")
    }

    private fun resolveFromDisk(): QWeatherCredentials {
        val projectId = prefs.getString(KEY_PROJECT_ID, "")?.trim().orEmpty()
        val keyId = prefs.getString(KEY_KEY_ID, "")?.trim().orEmpty()
        val privateKeyPem = prefs.getString(KEY_PRIVATE_KEY_PEM, "")?.trim().orEmpty()
        val apiHost = prefs.getString(KEY_API_HOST, "")?.trim().orEmpty()

        // 关键项全空视为「用户未配置」，直接回退编译注入
        if (projectId.isEmpty() && keyId.isEmpty() && privateKeyPem.isEmpty()) {
            return buildInjected
        }

        return QWeatherCredentials(
            projectId = projectId,
            keyId = keyId,
            privateKeyPem = privateKeyPem,
            apiHost = apiHost.ifEmpty { QWeatherCredentials.DEFAULT_API_HOST },
            source = QWeatherCredentialSource.USER
        )
    }

    companion object {
        private const val TAG = "QWeatherCredentialStore"
        private const val PREFS_NAME = "skypulse_qweather_credentials"
        private const val KEY_PROJECT_ID = "qweather_project_id"
        private const val KEY_KEY_ID = "qweather_key_id"
        private const val KEY_PRIVATE_KEY_PEM = "qweather_private_key_pem"
        private const val KEY_API_HOST = "qweather_api_host"
    }
}