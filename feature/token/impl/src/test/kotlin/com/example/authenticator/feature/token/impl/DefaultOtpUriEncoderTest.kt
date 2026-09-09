package com.example.authenticator.feature.token.impl

import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.arch.api.OtpParameters
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.common.basic.SensitiveBytes
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultOtpUriEncoderTest {

    private val codec = object : EncodingCodec {
        override fun base32EncodeNoPadding(bytes: ByteArray): String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".let { alphabet ->
                val out = StringBuilder()
                var buffer = 0
                var bits = 0
                for (b in bytes) {
                    buffer = (buffer shl 8) or (b.toInt() and 0xff)
                    bits += 8
                    while (bits >= 5) {
                        out.append(alphabet[(buffer shr (bits - 5)) and 0x1f])
                        bits -= 5
                    }
                }
                if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 0x1f])
                out.toString()
            }

        override fun base64Encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

        override fun percentEncode(text: String): String = text
    }

    private val encoder = DefaultOtpUriEncoder(codec)

    @Test
    fun `encodes full otpauth uri with issuer`() {
        val account = SensitiveAccount(
            AccountSummary(AccountId("id-1"), "Example", "alice@example.com", 1_700_000_000_000),
            OtpParameters(OtpAlgorithm.SHA1, 6, 30),
            SensitiveBytes("foobar".toByteArray()),
        )
        val uri = encoder.encode(account)
        assertEquals(
            "otpauth://totp/Example:alice@example.com?secret=MZXW6YTBOI&issuer=Example&algorithm=SHA1&digits=6&period=30",
            uri,
        )
    }

    @Test
    fun `encodes uri without issuer param when issuer empty`() {
        val account = SensitiveAccount(
            AccountSummary(AccountId("id-2"), "", "onlyname", 1_700_000_000_000),
            OtpParameters(OtpAlgorithm.SHA256, 8, 60),
            SensitiveBytes("foobar".toByteArray()),
        )
        val uri = encoder.encode(account)
        assertEquals(
            "otpauth://totp/onlyname?secret=MZXW6YTBOI&algorithm=SHA256&digits=8&period=60",
            uri,
        )
    }
}