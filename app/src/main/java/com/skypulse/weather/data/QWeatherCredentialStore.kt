package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import com.skypulse.weather.data.remote.qweather.Ed25519KeyPairPem
import com.skypulse.weather.data.remote.qweather.QWeatherCredentials
import com.skypulse.weather.data.remote.qweather.QWeatherCredentialSource
import com.skypulse.weather.data.remote.qweather.QWeatherKeyManager
import com.skypulse.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气凭据存储与安全密钥管理。
 *
 * 存储位置规范（严格遵从系统安全与合规要求）：
 * 1. 主存储：应用私有内部存储（Context.MODE_PRIVATE SharedPreferences），由 Linux UID 沙箱严格隔离；
 * 2. 冗余恢复备份：应用专属外部沙盒目录（context.getExternalFilesDir("credentials")），
 *    路径为 /sdcard/Android/data/com.skypulse.weather/files/credentials/；
 *    - 严禁在用户手机根目录（/sdcard/ 或公共外部存储）创建任何散乱文件；
 *    - 应用覆盖安装升级时，系统完整保留内部与私有外部沙盒数据；
 *    - 若内部缓存被极端清理误清空，启动时可无感知从沙盒备份自动恢复；
 *    - 卸载时系统联动清除，不留下任何垃圾。
 */
@Singleton
class QWeatherCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
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
        // 尝试从应用专属沙盒备份同步（预防升级后 prefs 损坏或被清理）
        restoreFromSandboxBackupIfNeeded()
        // v4.1.2：清理 v4.1.1 期间可能写入的无效 API Host
        dropInvalidApiHostEntry()
    }

    /**
     * 清除存储中不可用的 API Host。
     *
     * v4.1.1 曾把臆造域名 `https://devapi.qweatherapi.com/` 归一化后写入本键，
     * 若不清除，升级后仍会带着这个必然 DNS 解析失败的域名重蹈覆辙。
     */
    private fun dropInvalidApiHostEntry() {
        try {
            val stored = prefs.getString(KEY_API_HOST, "")?.trim().orEmpty()
            if (stored.isNotEmpty() && !QWeatherCredentials.isApiHostUsable(stored)) {
                prefs.edit().remove(KEY_API_HOST).apply()
                FileLogger.w(TAG, "invalid stored API host dropped: $stored")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to drop invalid API host entry: ${e.message}")
        }
    }

    /** 当前是否由用户在设置页提供了凭据。 */
    val isUserProvided: Boolean
        get() = _resolved.value.source == QWeatherCredentialSource.USER

    /** 编译期内置的凭据（v4.1.1 起不再内置凭据，统一通过设置页录入或自动生成公钥）。 */
    val buildInjected: QWeatherCredentials
        get() = QWeatherCredentials(source = QWeatherCredentialSource.BUILD)

    /**
     * 同步解析当前凭据。OkHttp 拦截器在任意线程调用，必须是非挂起函数。
     */
    fun resolveCurrent(): QWeatherCredentials = resolveFromDisk()

    /**
     * 获取当前可用的公钥信息（如果已有私钥则推导公钥，若未生成过则自动为用户生成一套全新密钥对）。
     * 保证用户进入设置页即可直接获得可复制的公钥文本及 SHA256 指纹。
     */
    @Synchronized
    fun getOrGenerateKeyPair(): Ed25519KeyPairPem {
        val currentPriv = prefs.getString(KEY_PRIVATE_KEY_PEM, "")?.trim().orEmpty()
        if (currentPriv.isNotEmpty()) {
            val derivedPub = QWeatherKeyManager.derivePublicKeyPem(currentPriv)
            if (derivedPub != null) {
                val sha256 = QWeatherKeyManager.calculatePublicKeySha256(derivedPub).orEmpty()
                return Ed25519KeyPairPem(
                    privateKeyPem = currentPriv,
                    publicKeyPem = derivedPub,
                    publicKeySha256 = sha256
                )
            }
        }

        // 尚未生成过有效密钥或当前为空，自动生成全新密钥对
        val freshPair = QWeatherKeyManager.generateKeyPair()
        prefs.edit()
            .putString(KEY_PRIVATE_KEY_PEM, freshPair.privateKeyPem)
            .putString(KEY_PUBLIC_KEY_PEM, freshPair.publicKeyPem)
            .apply()

        syncToSandboxBackup()
        return freshPair
    }

    /**
     * 用户显式请求重新生成一套全新的密钥对。
     */
    @Synchronized
    fun regenerateKeyPair(): Ed25519KeyPairPem {
        val freshPair = QWeatherKeyManager.generateKeyPair()
        prefs.edit()
            .putString(KEY_PRIVATE_KEY_PEM, freshPair.privateKeyPem)
            .putString(KEY_PUBLIC_KEY_PEM, freshPair.publicKeyPem)
            .apply()

        syncToSandboxBackup()
        return freshPair
    }

    /** 保存用户填写的凭据；字段会做 trim 与空串归一化。 */
    fun save(projectId: String, keyId: String, privateKeyPem: String, apiHost: String) {
        val trimmedHost = apiHost.trim()
        val normalizedApiHost = if (trimmedHost.isEmpty()) {
            QWeatherCredentials.DEFAULT_API_HOST
        } else {
            trimmedHost
        }
        val trimmedPriv = privateKeyPem.trim()
        val derivedPub = if (trimmedPriv.isNotEmpty()) {
            QWeatherKeyManager.derivePublicKeyPem(trimmedPriv)
        } else null

        val editor = prefs.edit()
            .putString(KEY_PROJECT_ID, projectId.trim())
            .putString(KEY_KEY_ID, keyId.trim())
            .putString(KEY_PRIVATE_KEY_PEM, trimmedPriv)
            .putString(KEY_API_HOST, normalizedApiHost)

        if (derivedPub != null) {
            editor.putString(KEY_PUBLIC_KEY_PEM, derivedPub)
        }
        editor.apply()

        syncToSandboxBackup()
        FileLogger.i(TAG, "user QWeather credentials saved (host=$normalizedApiHost)")
    }

    /** 清除用户凭据，回退到编译内置（若编译也未注入，则为未配置状态）。 */
    fun clear() {
        prefs.edit().clear().apply()
        clearSandboxBackup()
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

    /**
     * 将凭据安全同步至应用沙盒专属目录备份。
     * 绝不在手机根目录建立文件，只写入 Context.getExternalFilesDir("credentials")。
     */
    private fun syncToSandboxBackup() {
        try {
            val backupDir = context.getExternalFilesDir("credentials") ?: return
            if (!backupDir.exists()) backupDir.mkdirs()
            val backupFile = File(backupDir, BACKUP_FILE_NAME)

            val json = JSONObject().apply {
                put(KEY_PROJECT_ID, prefs.getString(KEY_PROJECT_ID, ""))
                put(KEY_KEY_ID, prefs.getString(KEY_KEY_ID, ""))
                put(KEY_PRIVATE_KEY_PEM, prefs.getString(KEY_PRIVATE_KEY_PEM, ""))
                put(KEY_PUBLIC_KEY_PEM, prefs.getString(KEY_PUBLIC_KEY_PEM, ""))
                put(KEY_API_HOST, prefs.getString(KEY_API_HOST, ""))
                put("updated_at", System.currentTimeMillis())
            }

            backupFile.writeText(json.toString())
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to sync credentials to sandbox backup: ${e.message}")
        }
    }

    /**
     * 当内部 SharedPreferences 为空时，尝试从专属沙盒备份恢复。
     */
    private fun restoreFromSandboxBackupIfNeeded() {
        try {
            val hasLocalKey = prefs.getString(KEY_PRIVATE_KEY_PEM, "")?.isNotEmpty() == true
            if (hasLocalKey) return

            val backupDir = context.getExternalFilesDir("credentials") ?: return
            val backupFile = File(backupDir, BACKUP_FILE_NAME)
            if (!backupFile.exists()) return

            val content = backupFile.readText().trim()
            if (content.isEmpty()) return

            val json = JSONObject(content)
            val priv = json.optString(KEY_PRIVATE_KEY_PEM, "")
            val restoredHost = json.optString(KEY_API_HOST, "").trim()
            if (priv.isNotEmpty()) {
                prefs.edit()
                    .putString(KEY_PROJECT_ID, json.optString(KEY_PROJECT_ID, ""))
                    .putString(KEY_KEY_ID, json.optString(KEY_KEY_ID, ""))
                    .putString(KEY_PRIVATE_KEY_PEM, priv)
                    .putString(KEY_PUBLIC_KEY_PEM, json.optString(KEY_PUBLIC_KEY_PEM, ""))
                    .putString(KEY_API_HOST, restoredApiHostOrDefault(restoredHost))
                    .apply()
                FileLogger.i(TAG, "Successfully restored QWeather credentials from sandbox backup")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to restore credentials from sandbox backup: ${e.message}")
        }
    }

    /** 恢复时的 Host 归一化：无效值（臆造域名 / 旧公共域名）一律丢弃，避免带病恢复。 */
    private fun restoredApiHostOrDefault(stored: String): String =
        if (stored.isNotEmpty() && QWeatherCredentials.isApiHostUsable(stored)) {
            stored
        } else {
            QWeatherCredentials.DEFAULT_API_HOST
        }

    private fun clearSandboxBackup() {
        try {
            val backupDir = context.getExternalFilesDir("credentials") ?: return
            val backupFile = File(backupDir, BACKUP_FILE_NAME)
            if (backupFile.exists()) {
                backupFile.delete()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "QWeatherCredentialStore"
        private const val PREFS_NAME = "skypulse_qweather_credentials"
        private const val BACKUP_FILE_NAME = "qweather_credentials.json"
        private const val KEY_PROJECT_ID = "qweather_project_id"
        private const val KEY_KEY_ID = "qweather_key_id"
        private const val KEY_PRIVATE_KEY_PEM = "qweather_private_key_pem"
        private const val KEY_PUBLIC_KEY_PEM = "qweather_public_key_pem"
        private const val KEY_API_HOST = "qweather_api_host"
    }
}