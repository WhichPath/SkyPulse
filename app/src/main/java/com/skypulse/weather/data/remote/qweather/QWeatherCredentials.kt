package com.skypulse.weather.data.remote.qweather

/**
 * 和风天气凭据来源。
 *
 * v4.0.2 起，密钥环节与编译解耦：
 * - [USER]：用户在设置页「和风天气 API」分区手动填写，仅保存在本机 SharedPreferences；
 * - [BUILD]：编译期经 local.properties / CI secrets 注入 BuildConfig（历史方式，继续保留为回退）。
 *
 * 运行时解析优先级：用户填写 > 编译内置。
 */
enum class QWeatherCredentialSource {
    /** 用户在设置页手动填写 */
    USER,

    /** 编译期 BuildConfig 注入 */
    BUILD
}

/**
 * 一份完整的和风天气调用凭据。
 *
 * @param projectId 项目 ID（JWT payload 的 sub 字段）
 * @param keyId 凭据 ID（JWT header 的 kid 字段）
 * @param privateKeyPem Ed25519 私钥（PEM 文本，含 BEGIN/END 标记）
 * @param apiHost 项目 API Host
 * @param source 该凭据的来源
 */
data class QWeatherCredentials(
    val projectId: String = "",
    val keyId: String = "",
    val privateKeyPem: String = "",
    val apiHost: String = DEFAULT_API_HOST,
    val source: QWeatherCredentialSource = QWeatherCredentialSource.BUILD
) {

    /** 四个字段齐备才可发起鉴权请求。 */
    val isComplete: Boolean
        get() = projectId.isNotBlank() &&
            keyId.isNotBlank() &&
            privateKeyPem.isNotBlank() &&
            apiHost.isNotBlank()

    /** 经过规范化（补 scheme、补尾斜杠）的 API Host，可直接作为 Retrofit baseUrl。 */
    val normalizedApiHost: String
        get() = normalizeApiHost(apiHost)

    /**
     * 凭据指纹：用于 JWT 缓存失效判断。
     * 用户切换密钥后无需重启进程即可让缓存 token 立即失效并重新签名。
     */
    val fingerprint: String
        get() = "$projectId|$keyId|${privateKeyPem.trim().hashCode()}|$normalizedApiHost"

    /** 私钥是否形似 PEM（仅做形态校验，真正的解析在 JWT 生成器）。 */
    val hasPemShape: Boolean
        get() = privateKeyPem.contains("BEGIN") && privateKeyPem.contains("END")

    companion object {
        /** 和风官方开发/体验 Host，用户未填写 API Host 时的兜底。 */
        const val DEFAULT_API_HOST = "https://devapi.qweatherapi.com/"

        /**
         * 规范化 API Host：
         * - 补全缺失的 https:// 前缀
         * - 补全结尾斜杠（Retrofit 要求 baseUrl 以 / 结尾）
         * - 去掉首尾空白
         */
        fun normalizeApiHost(raw: String): String {
            var host = raw.trim()
            if (host.isEmpty()) return DEFAULT_API_HOST
            if (!host.startsWith("http://") && !host.startsWith("https://")) {
                host = "https://$host"
            }
            if (!host.endsWith("/")) host = "$host/"
            return host
        }
    }
}