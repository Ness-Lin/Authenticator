package com.example.authenticator.feature.token.impl

import com.example.authenticator.arch.api.OtpAlgorithm

/**
 * 纯 TOTP 计算核心（RFC 6238 / RFC 4226）。
 *
 * 只负责计数器编码、动态截断与十进制格式化，不实现 HMAC 原语；HMAC 由 [TotpEngine.generate]
 * 的 [hmac] 参数注入（生产路径来自 CryptoProvider，测试路径来自 JCA）。
 */
internal object TotpEngine {

    internal data class Output(val code: String, val validFromMillis: Long, val validUntilMillis: Long)

    /** 时间步计数器：T = floor((epochSeconds - T0) / period)，T0 = 0。 */
    fun counter(epochSeconds: Long, periodSeconds: Int): Long = epochSeconds / periodSeconds

    /** 计数器编码为 8 字节大端无符号整数，供 HMAC 消息使用。 */
    fun counterBytes(counter: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) {
            out[7 - i] = (counter ushr (8 * i) and 0xff).toByte()
        }
        return out
    }

    /** RFC 4226 动态截断：取末字节低 4 位作为偏移，组装 31 位正整数。 */
    fun dynamicTruncation(digest: ByteArray): Int {
        require(digest.size >= 4) { "HMAC digest too short" }
        val offset = digest[digest.size - 1].toInt() and 0x0f
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or
            ((digest[offset + 1].toInt() and 0xff) shl 16) or
            ((digest[offset + 2].toInt() and 0xff) shl 8) or
            (digest[offset + 3].toInt() and 0xff)
        return binary
    }

    /** 对 10^digits 取模并左侧补零。 */
    fun format(truncated: Int, digits: Int): String {
        require(digits == 6 || digits == 8) { "TOTP digits must be 6 or 8" }
        val mod = truncated % pow10(digits)
        return mod.toString().padStart(digits, '0')
    }

    private fun pow10(n: Int): Int {
        var r = 1
        repeat(n) { r *= 10 }
        return r
    }

    /** 端到端生成验证码；[hmac] 负责给定算法的摘要计算。 */
    suspend fun generate(
        secret: ByteArray,
        algorithm: OtpAlgorithm,
        digits: Int,
        periodSeconds: Int,
        epochMillis: Long,
        hmac: suspend (OtpAlgorithm, ByteArray, ByteArray) -> ByteArray,
    ): Output {
        val epochSeconds = epochMillis / 1000L
        require(epochSeconds >= 0) { "Device time before Unix epoch" }
        val c = counter(epochSeconds, periodSeconds)
        val digest = hmac(algorithm, secret, counterBytes(c))
        val code = format(dynamicTruncation(digest), digits)
        val periodMillis = periodSeconds * 1000L
        return Output(code, c * periodMillis, (c + 1) * periodMillis)
    }
}