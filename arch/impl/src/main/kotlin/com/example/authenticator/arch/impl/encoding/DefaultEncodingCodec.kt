package com.example.authenticator.arch.impl.encoding

import com.example.authenticator.arch.api.EncodingCodec

/** RFC 4648 编码实现，纯 Kotlin，无 Android 依赖。 */
internal class DefaultEncodingCodec : EncodingCodec {

    private val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    override fun base32EncodeNoPadding(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                out.append(base32Alphabet[(buffer shr (bitsLeft - 5)) and 0x1f])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            out.append(base32Alphabet[(buffer shl (5 - bitsLeft)) and 0x1f])
        }
        return out.toString()
    }

    override fun base64Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xff
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else -1
            out.append(base64Alphabet[b0 shr 2])
            out.append(base64Alphabet[(b0 and 0x03 shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            out.append(if (b1 >= 0) base64Alphabet[(b1 and 0x0f shl 2) or (if (b2 >= 0) b2 shr 6 else 0)] else '=')
            out.append(if (b2 >= 0) base64Alphabet[b2 and 0x3f] else '=')
            i += 3
        }
        return out.toString()
    }

    override fun percentEncode(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch)
            } else {
                out.append('%')
                out.append(HEX[c ushr 4])
                out.append(HEX[c and 0x0f])
            }
        }
        return out.toString()
    }

    private companion object {
        const val HEX = "0123456789ABCDEF"
    }
}