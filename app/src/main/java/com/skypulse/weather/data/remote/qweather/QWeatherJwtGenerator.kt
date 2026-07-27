package com.skypulse.weather.data.remote.qweather

import android.util.Base64
import com.skypulse.weather.util.FileLogger
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气 JWT 生成器。
 *
 * 使用 Ed25519 算法对 JWT 进行签名（RFC 7519 + EdDSA）。
 * 私钥由用户在 local.properties 中配置（PEM 格式的 PKCS#8 Ed25519 私钥）。
 * 公钥需上传到和风天气控制台。
 *
 * 生成的 JWT 通过 Authorization: Bearer 头发送给和风 API。
 *
 * @param projectId 项目 ID（JWT payload 的 sub 字段）
 * @param keyId 凭据 ID（JWT header 的 kid 字段）
 * @param privateKeyPem Ed25519 私钥（PEM 格式，含 BEGIN/END 标记）
 */
@Singleton
class QWeatherJwtGenerator @Inject constructor() {

    companion object {
        private const val TAG = "QWeatherJwt"
        private const val TOKEN_TTL_SECONDS = 900L // 15 分钟
    }

    fun generateToken(projectId: String, keyId: String, privateKeyPem: String): String? {
        return try {
            val rawKeyBytes = extractRawKeyBytes(privateKeyPem)
                ?: run {
                    FileLogger.e(TAG, "Failed to extract Ed25519 private key from PEM")
                    return null
                }

            val privateKeyParams = Ed25519PrivateKeyParameters(rawKeyBytes, 0)

            // Header
            val header = JSONObject().apply {
                put("alg", "EdDSA")
                put("kid", keyId)
            }

            // Payload
            val now = System.currentTimeMillis() / 1000
            val payload = JSONObject().apply {
                put("sub", projectId)
                put("iat", now - 30) // 偏移 30 秒防止时钟误差
                put("exp", now + TOKEN_TTL_SECONDS)
            }

            // Base64URL 编码
            val headerEncoded = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
            val payloadEncoded = base64UrlEncode(payload.toString().toByteArray(Charsets.UTF_8))
            val signingInput = "$headerEncoded.$payloadEncoded"

            // Ed25519 签名
            val signer = Ed25519Signer()
            signer.init(true, privateKeyParams)
            val inputBytes = signingInput.toByteArray(Charsets.UTF_8)
            signer.update(inputBytes, 0, inputBytes.size)
            val signature = signer.generateSignature()
            val signatureEncoded = base64UrlEncode(signature)

            "$signingInput.$signatureEncoded"
        } catch (e: Exception) {
            FileLogger.e(TAG, "JWT generation failed: ${e.message}", e)
            null
        }
    }

    /**
     * 从 PEM 格式的 PKCS#8 Ed25519 私钥中提取原始 32 字节密钥。
     *
     * PKCS#8 编码的 Ed25519 私钥固定为 48 字节：16 字节 ASN.1 前缀 + 32 字节原始密钥。
     */
    private fun extractRawKeyBytes(pem: String): ByteArray? {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN ED25519 PRIVATE KEY-----", "")
            .replace("-----END ED25519 PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        return try {
            val derBytes = Base64.decode(cleaned, Base64.NO_WRAP)
            when (derBytes.size) {
                32 -> derBytes // 已经是原始密钥
                48 -> derBytes.copyOfRange(16, 48) // PKCS#8：去掉 16 字节前缀
                else -> {
                    FileLogger.e(TAG, "Unexpected Ed25519 key length: ${derBytes.size}")
                    null
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to decode private key Base64: ${e.message}", e)
            null
        }
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(
            data,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
