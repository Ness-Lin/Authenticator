package com.example.authenticator.feature.transfer.impl

import kotlinx.serialization.Serializable

/** 加密导出外层信封（v1）。ciphertext 为密文与认证标签拼接后的 Base64。 */
@Serializable
internal data class BackupEnvelope(
    val format: String,
    val version: Int,
    val kdf: BackupKdf,
    val cipher: BackupCipher,
    val ciphertext: String,
)

@Serializable
internal data class BackupKdf(
    val name: String,
    val iterations: Int,
    val salt: String,
)

@Serializable
internal data class BackupCipher(
    val name: String,
    val nonce: String,
    val tagBits: Int,
)

/** 加密导出内层明文负载（v1），全部账户信息位于密文内部。 */
@Serializable
internal data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: String,
    val accounts: List<BackupAccount>,
)

@Serializable
internal data class BackupAccount(
    val id: String,
    val issuer: String,
    val accountName: String,
    val type: String,
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val createdAt: String,
)