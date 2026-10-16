package com.skypulse.weather.data.remote.qweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QWeatherKeyManagerTest {

    @Test
    fun testGenerateKeyPairProducesValidPemAndFingerprint() {
        val keyPair = QWeatherKeyManager.generateKeyPair()

        assertTrue(keyPair.privateKeyPem.startsWith("-----BEGIN PRIVATE KEY-----"))
        assertTrue(keyPair.privateKeyPem.endsWith("-----END PRIVATE KEY-----"))

        assertTrue(keyPair.publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(keyPair.publicKeyPem.endsWith("-----END PUBLIC KEY-----"))

        assertTrue(keyPair.publicKeySha256.isNotBlank())
        // SHA-256 in hex format with colons: 32 bytes = 32 pairs of hex chars + 31 colons = 95 chars
        assertEquals(95, keyPair.publicKeySha256.length)

        // Verify private key extraction
        val rawPriv = QWeatherKeyManager.extractRawPrivateKeyBytes(keyPair.privateKeyPem)
        assertNotNull(rawPriv)
        assertEquals(32, rawPriv!!.size)

        // Derive public key from private key
        val derivedPub = QWeatherKeyManager.derivePublicKeyPem(keyPair.privateKeyPem)
        assertNotNull(derivedPub)
        assertEquals(keyPair.publicKeyPem, derivedPub)

        // Verify SHA256 matches
        val derivedSha256 = QWeatherKeyManager.calculatePublicKeySha256(derivedPub!!)
        assertEquals(keyPair.publicKeySha256, derivedSha256)
    }

    @Test
    fun testJwtGeneratorCanSignWithGeneratedKey() {
        val keyPair = QWeatherKeyManager.generateKeyPair()
        val generator = QWeatherJwtGenerator()

        val token = generator.generateToken(
            projectId = "test-project-123",
            keyId = "test-key-456",
            privateKeyPem = keyPair.privateKeyPem
        )

        assertNotNull(token)
        assertTrue(token!!.split(".").size == 3)
    }
}
