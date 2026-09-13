package com.example.authenticator.common.basic

sealed interface CoreError {
    data class InvalidInput(val reason: String? = null) : CoreError
    data object NotFound : CoreError
    data object Unauthorized : CoreError
    data class Failure(val cause: Throwable? = null) : CoreError
}

/** 受控敏感字节：调用方不得持久化、序列化或放入 UI 状态。 */
class SensitiveBytes(bytes: ByteArray) {
    private var value: ByteArray? = bytes.copyOf()

    fun copy(): ByteArray = value?.copyOf() ?: ByteArray(0)
    fun clear() { value?.fill(0); value = null }
    override fun toString(): String = "SensitiveBytes(cleared=${value == null})"
}

typealias SecretBytes = SensitiveBytes
typealias PasswordBytes = SensitiveBytes
