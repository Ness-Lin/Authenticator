package com.example.authenticator.feature.token.impl

import com.example.authenticator.arch.api.OtpAlgorithm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TotpEngineTest {

    // RFC 6238 各算法使用不同长度的种子：
    // SHA-1 用 20 字节、SHA-256 用 32 字节、SHA-512 用 64 字节。
    private val sha1Seed = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val sha256Seed = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
    private val sha512Seed = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)

    private fun hmac(algorithm: OtpAlgorithm, secret: ByteArray, message: ByteArray): ByteArray {
        val macName = when (algorithm) {
            OtpAlgorithm.SHA1 -> "HmacSHA1"
            OtpAlgorithm.SHA256 -> "HmacSHA256"
            OtpAlgorithm.SHA512 -> "HmacSHA512"
        }
        val mac = Mac.getInstance(macName)
        mac.init(SecretKeySpec(secret, macName))
        return mac.doFinal(message)
    }

    private fun totp(algorithm: OtpAlgorithm, epochSeconds: Long): String {
        val secret = when (algorithm) {
            OtpAlgorithm.SHA1 -> sha1Seed
            OtpAlgorithm.SHA256 -> sha256Seed
            OtpAlgorithm.SHA512 -> sha512Seed
        }
        return runBlocking {
            TotpEngine.generate(secret, algorithm, digits = 8, periodSeconds = 30, epochMillis = epochSeconds * 1000L) { a, s, m -> hmac(a, s, m) }.code
        }
    }

    @Test
    fun `RFC 6238 SHA1 vectors`() {
        assertEquals("94287082", totp(OtpAlgorithm.SHA1, 59L))
        assertEquals("07081804", totp(OtpAlgorithm.SHA1, 1111111109L))
        assertEquals("14050471", totp(OtpAlgorithm.SHA1, 1111111111L))
        assertEquals("89005924", totp(OtpAlgorithm.SHA1, 1234567890L))
        assertEquals("69279037", totp(OtpAlgorithm.SHA1, 2000000000L))
        assertEquals("65353130", totp(OtpAlgorithm.SHA1, 20000000000L))
    }

    @Test
    fun `RFC 6238 SHA256 vectors`() {
        assertEquals("46119246", totp(OtpAlgorithm.SHA256, 59L))
        assertEquals("68084774", totp(OtpAlgorithm.SHA256, 1111111109L))
        assertEquals("67062674", totp(OtpAlgorithm.SHA256, 1111111111L))
        assertEquals("91819424", totp(OtpAlgorithm.SHA256, 1234567890L))
        assertEquals("90698825", totp(OtpAlgorithm.SHA256, 2000000000L))
        assertEquals("77737706", totp(OtpAlgorithm.SHA256, 20000000000L))
    }

    @Test
    fun `RFC 6238 SHA512 vectors`() {
        assertEquals("90693936", totp(OtpAlgorithm.SHA512, 59L))
        assertEquals("25091201", totp(OtpAlgorithm.SHA512, 1111111109L))
        assertEquals("99943326", totp(OtpAlgorithm.SHA512, 1111111111L))
        assertEquals("93441116", totp(OtpAlgorithm.SHA512, 1234567890L))
        assertEquals("38618901", totp(OtpAlgorithm.SHA512, 2000000000L))
        assertEquals("47863826", totp(OtpAlgorithm.SHA512, 20000000000L))
    }

    @Test
    fun `counter adopts floor of time step`() {
        // period=30，59 秒落入第 1 步；90 秒恰好进入第 3 步。
        assertEquals(1L, TotpEngine.counter(59L, 30))
        assertEquals(3L, TotpEngine.counter(90L, 30))
    }

    @Test
    fun `format pads leading zeros to fixed width`() {
        assertEquals("004583", TotpEngine.format(4583, 6))
        assertEquals("00000004", TotpEngine.format(4, 8))
    }
}