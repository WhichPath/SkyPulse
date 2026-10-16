package com.ridgecast.weather.data.remote.qweather

import com.ridgecast.weather.util.FileLogger
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Ed25519 密钥对数据载体（包含 PEM 文本与公钥指纹）。
 */
data class Ed25519KeyPairPem(
    val privateKeyPem: String,
    val publicKeyPem: String,
    val publicKeySha256: String
)

/**
 * 和风天气 Ed25519 密钥管理器。
 *
 * 负责：
 * 1. 一键生成符合 RFC 8410 标准的 Ed25519 密钥对（PKCS#8 私钥与 X.509 SPKI 公钥 PEM 格式）；
 * 2. 从已有私钥 PEM 派生标准公钥 PEM；
 * 3. 计算公钥 SHA-256 指纹（供用户核对和风控制台上传的公钥）；
 * 4. 提取原始私钥字节供 JWT 签名器使用。
 */
object QWeatherKeyManager {

    private const val TAG = "QWeatherKeyManager"

    /**
     * 生成全新的 Ed25519 密钥对。
     */
    fun generateKeyPair(): Ed25519KeyPairPem {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val privateKeyParams = keyPair.private as Ed25519PrivateKeyParameters
        val publicKeyParams = keyPair.public as Ed25519PublicKeyParameters

        val privateKeyPem = encodePrivateKeyToPem(privateKeyParams)
        val publicKeyPem = encodePublicKeyToPem(publicKeyParams)
        val sha256 = calculatePublicKeySha256(publicKeyPem).orEmpty()

        FileLogger.i(TAG, "Generated fresh Ed25519 key pair with SHA256=$sha256")
        return Ed25519KeyPairPem(
            privateKeyPem = privateKeyPem,
            publicKeyPem = publicKeyPem,
            publicKeySha256 = sha256
        )
    }

    /**
     * 从 PEM 格式的私钥中自动派生标准公钥 PEM 文本。
     * 支持包含 PKCS#8 头部的 PEM 以及 32 字节原始密钥。
     */
    fun derivePublicKeyPem(privateKeyPem: String): String? {
        val rawBytes = extractRawPrivateKeyBytes(privateKeyPem) ?: return null
        return try {
            val privateKeyParams = Ed25519PrivateKeyParameters(rawBytes, 0)
            val publicKeyParams = privateKeyParams.generatePublicKey()
            encodePublicKeyToPem(publicKeyParams)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to derive public key: ${e.message}", e)
            null
        }
    }

    /**
     * 计算公钥的 SHA-256 指纹（以常见十六进制大写形式，带冒号分隔，便于用户与和风控制台核对）。
     * 对 X.509 SPKI DER 二进制字节进行哈希计算。
     */
    fun calculatePublicKeySha256(publicKeyPem: String): String? {
        val derBytes = decodePemToDer(publicKeyPem, "PUBLIC KEY") ?: return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(derBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to calculate SHA-256 for public key: ${e.message}", e)
            null
        }
    }

    /**
     * 从 PEM 文本提取 32 字节原始私钥。
     * 支持 PKCS#8 v1 (48字节), PKCS#8 v2 (83字节含公钥), 以及 32 字节原始私钥。
     */
    fun extractRawPrivateKeyBytes(pem: String): ByteArray? {
        val derBytes = decodePemToDer(pem, "PRIVATE KEY")
            ?: decodePemToDer(pem, "ED25519 PRIVATE KEY")
            ?: return null

        return try {
            if (derBytes.size == 32) {
                return derBytes
            }
            val pki = org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(derBytes)
            val privateKey = pki.parsePrivateKey()
            val octets = org.bouncycastle.asn1.ASN1OctetString.getInstance(privateKey).octets
            if (octets.size == 32) {
                octets
            } else if (octets.size > 32) {
                // 部分实现将 32 字节私钥封装在额外的 OctetString 中
                octets.copyOfRange(octets.size - 32, octets.size)
            } else {
                null
            }
        } catch (e: Exception) {
            // 回退到基于偏移量的粗提取
            when (derBytes.size) {
                32 -> derBytes
                48 -> derBytes.copyOfRange(16, 48)
                else -> {
                    FileLogger.e(TAG, "Unexpected private key DER length: ${derBytes.size}")
                    null
                }
            }
        }
    }

    private fun encodePrivateKeyToPem(params: Ed25519PrivateKeyParameters): String {
        val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(params)
        return formatPem("PRIVATE KEY", privateKeyInfo.encoded)
    }

    private fun encodePublicKeyToPem(params: Ed25519PublicKeyParameters): String {
        val spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(params)
        return formatPem("PUBLIC KEY", spki.encoded)
    }

    private fun formatPem(type: String, derBytes: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(derBytes)
        val wrapped = base64.chunked(64).joinToString("\n")
        return "-----BEGIN $type-----\n$wrapped\n-----END $type-----"
    }

    private fun decodePemToDer(pem: String, type: String): ByteArray? {
        val header = "-----BEGIN $type-----"
        val footer = "-----END $type-----"
        val cleaned = pem
            .replace(header, "")
            .replace(footer, "")
            .replace("\\s".toRegex(), "")

        if (cleaned.isEmpty()) return null

        return try {
            Base64.getDecoder().decode(cleaned)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to decode PEM Base64 for type $type: ${e.message}", e)
            null
        }
    }
}
