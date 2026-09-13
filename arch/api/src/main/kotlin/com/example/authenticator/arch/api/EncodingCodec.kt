package com.example.authenticator.arch.api

/**
 * 协议编码能力契约。
 *
 * 提供与密码学无关的编码转换，供 URI 序列化与导出格式使用。实现归 arch:impl，
 * 不引入 Android 类型，便于单元测试与后续 KMP 共享。
 */
interface EncodingCodec {
    /** RFC 4648 Base32，大写字母表，不输出填充字符。 */
    fun base32EncodeNoPadding(bytes: ByteArray): String

    /** RFC 4648 Base64，标准字母表，带填充，无换行。 */
    fun base64Encode(bytes: ByteArray): String

    /** 按 RFC 3986 对 UTF-8 字节做百分号编码，保留 unreserved 字符。 */
    fun percentEncode(text: String): String
}