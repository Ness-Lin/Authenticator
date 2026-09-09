package com.example.authenticator.arch.impl.storage

import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.arch.api.OtpParameters
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.common.basic.SensitiveBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * 本地加密负载编解码。负载只在内存中明文出现，落库前交由 CryptoProvider 加密。
 *
 * 负载字段：id、issuer、accountName、algorithm、digits、periodSeconds、createdAt、secret。
 * 解密后必须校验内部 id 与外层主键一致（[decode] 的 [expectedId] 参数）。
 */
internal object RecordPayloadCodec {

    private const val CURRENT_VERSION = 1

    fun encode(account: SensitiveAccount): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(CURRENT_VERSION)
            d.writeUTF(account.summary.id.value)
            d.writeUTF(account.summary.issuer)
            d.writeUTF(account.summary.accountName)
            d.writeInt(account.parameters.algorithm.ordinal)
            d.writeInt(account.parameters.digits)
            d.writeInt(account.parameters.periodSeconds)
            d.writeLong(account.summary.createdAtEpochMillis)
            val secret = account.secret.copy()
            d.writeInt(secret.size)
            d.write(secret)
            secret.fill(0)
        }
        return out.toByteArray()
    }

    fun decode(expectedId: AccountId, payload: ByteArray): SensitiveAccount {
        require(payload.isNotEmpty()) { "Empty payload" }
        return DataInputStream(ByteArrayInputStream(payload)).use { d ->
            val version = d.readInt()
            require(version == CURRENT_VERSION) { "Unsupported payload version: $version" }
            val id = AccountId(d.readUTF())
            require(id == expectedId) { "Payload id mismatch" }
            val issuer = d.readUTF()
            val accountName = d.readUTF()
            val algorithm = OtpAlgorithm.entries[d.readInt()]
            val digits = d.readInt()
            val periodSeconds = d.readInt()
            val createdAt = d.readLong()
            val secretSize = d.readInt()
            val secret = ByteArray(secretSize)
            d.readFully(secret)
            val summary = AccountSummary(id, issuer, accountName, createdAt)
            SensitiveAccount(summary, OtpParameters(algorithm, digits, periodSeconds), SensitiveBytes(secret))
        }
    }
}