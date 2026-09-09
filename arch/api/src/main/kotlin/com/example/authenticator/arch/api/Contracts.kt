package com.example.authenticator.arch.api

import com.example.authenticator.common.basic.OperationResult
import com.example.authenticator.common.basic.SensitiveBytes
import kotlinx.coroutines.flow.Flow

@JvmInline value class AccountId(val value: String)
enum class OtpAlgorithm { SHA1, SHA256, SHA512 }
data class OtpParameters(val algorithm: OtpAlgorithm, val digits: Int, val periodSeconds: Int) {
    init {
        require(digits == 6 || digits == 8) { "TOTP digits must be 6 or 8" }
        require(periodSeconds in 15..120) { "TOTP period must be between 15 and 120 seconds" }
    }
}
data class AccountSummary(val id: AccountId, val issuer: String, val accountName: String, val createdAtEpochMillis: Long)
class SensitiveAccount(val summary: AccountSummary, val parameters: OtpParameters, val secret: SensitiveBytes) {
    override fun toString(): String = "SensitiveAccount([REDACTED])"
}
@JvmInline value class AccessLease(val value: String) {
    override fun toString(): String = "AccessLease([REDACTED])"
}

enum class OperationType { Read, Write, Export }
data class OperationBinding(val operation: OperationType, val accountIds: Set<AccountId> = emptySet(), val requestId: String? = null)

interface AccountRepository {
    fun observeSummaries(lease: AccessLease): Flow<List<AccountSummary>>
    suspend fun get(id: AccountId, lease: AccessLease): OperationResult<SensitiveAccount, RepositoryError>
    suspend fun save(account: SensitiveAccount, lease: AccessLease): OperationResult<AccountSummary, RepositoryError>
    suspend fun delete(id: AccountId, lease: AccessLease): OperationResult<Unit, RepositoryError>
    /** 一次性读取指定账户集合，用于导出等需要一致快照的场景；任一账户缺失即整体失败。 */
    suspend fun snapshot(ids: Set<AccountId>, lease: AccessLease): OperationResult<List<SensitiveAccount>, RepositoryError>
}
sealed interface RepositoryError {
    data object Conflict : RepositoryError
    data object NotFound : RepositoryError
    data object StorageFailure : RepositoryError
    data object Unauthorized : RepositoryError
}

interface CryptoProvider {
    suspend fun hmac(algorithm: OtpAlgorithm, secret: SensitiveBytes, message: ByteArray): ByteArray
    suspend fun deriveExportKey(password: SensitiveBytes, salt: ByteArray): SensitiveBytes
    suspend fun encryptExport(key: SensitiveBytes, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray
    suspend fun encryptRecord(keyVersion: Int, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray
    suspend fun decryptRecord(keyVersion: Int, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
    suspend fun randomBytes(size: Int): ByteArray
}
interface AccessGate {
    fun issue(binding: OperationBinding): AccessLease
    fun isValid(lease: AccessLease, binding: OperationBinding): Boolean
    fun revoke(lease: AccessLease)
    fun revokeAll()
}
