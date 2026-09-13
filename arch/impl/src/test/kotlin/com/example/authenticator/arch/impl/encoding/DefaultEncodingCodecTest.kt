package com.example.authenticator.arch.impl.encoding

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultEncodingCodecTest {

    private val codec = DefaultEncodingCodec()

    @Test
    fun `base32 no padding RFC 4648 vectors`() {
        assertEquals("", codec.base32EncodeNoPadding("".toByteArray()))
        assertEquals("MY", codec.base32EncodeNoPadding("f".toByteArray()))
        assertEquals("MZXQ", codec.base32EncodeNoPadding("fo".toByteArray()))
        assertEquals("MZXW6", codec.base32EncodeNoPadding("foo".toByteArray()))
        assertEquals("MZXW6YQ", codec.base32EncodeNoPadding("foob".toByteArray()))
        assertEquals("MZXW6YTB", codec.base32EncodeNoPadding("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", codec.base32EncodeNoPadding("foobar".toByteArray()))
    }

    @Test
    fun `base64 with padding RFC 4648 vectors`() {
        assertEquals("", codec.base64Encode("".toByteArray()))
        assertEquals("Zg==", codec.base64Encode("f".toByteArray()))
        assertEquals("Zm8=", codec.base64Encode("fo".toByteArray()))
        assertEquals("Zm9v", codec.base64Encode("foo".toByteArray()))
        assertEquals("Zm9vYg==", codec.base64Encode("foob".toByteArray()))
        assertEquals("Zm9vYmE=", codec.base64Encode("fooba".toByteArray()))
        assertEquals("Zm9vYmFy", codec.base64Encode("foobar".toByteArray()))
    }

    @Test
    fun `percent encode keeps unreserved and encodes others uppercase`() {
        assertEquals("abc-_.~", codec.percentEncode("abc-_.~"))
        assertEquals("%20", codec.percentEncode(" "))
        assertEquals("%3A", codec.percentEncode(":"))
        assertEquals("%40", codec.percentEncode("@"))
        assertEquals("Hello%20World", codec.percentEncode("Hello World"))
    }
}