package com.example.authenticator.arch.impl.storage

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccessLease
import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.OperationBinding
import com.example.authenticator.arch.api.OperationType
import com.example.authenticator.arch.api.RepositoryError
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.common.basic.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 加密账户仓库。
 *
 * 所有操作在访问数据前均通过 [AccessGate] 校验操作绑定；落库前由 [CryptoProvider] 加密，
 * 读取时解密并校验负载内 id 与外层主键一致。列表订阅只发布非敏感摘要。
 */
internal class RoomAccountRepository(
    private val dao: AccountDao,
    private val crypto: CryptoProvider,
    private val gate: AccessGate,
) : AccountRepository {

    override fun observeSummaries(lease: AccessLease): Flow<List<AccountSummary>> =
        dao.observeAll().map { entities ->
            if (!gate.isValid(lease, OperationBinding(OperationType.Read))) return@map emptyList()
            entities.map { entity ->
                val account = decryptEntity(entity)
                account.summary
            }
        }

    override suspend fun get(id: AccountId, lease: AccessLease): OperationResult<SensitiveAccount, RepositoryError> {
        if (!gate.isValid(lease, OperationBinding(OperationType.Read, setOf(id)))) {
            return OperationResult.Failure(RepositoryError.Unauthorized)
        }
        return try {
            val entity = dao.findById(id.value)
                ?: return OperationResult.Failure(RepositoryError.NotFound)
            OperationResult.Success(decryptEntity(entity))
        } catch (e: Exception) {
            OperationResult.Failure(RepositoryError.StorageFailure)
        }
    }

    override suspend fun save(account: SensitiveAccount, lease: AccessLease): OperationResult<AccountSummary, RepositoryError> {
        if (!gate.isValid(lease, OperationBinding(OperationType.Write, setOf(account.summary.id)))) {
            return OperationResult.Failure(RepositoryError.Unauthorized)
        }
        return try {
            val id = account.summary.id
            val existing = dao.findById(id.value)
            if (existing != null) {
                return OperationResult.Failure(RepositoryError.Conflict)
            }
            val revision = dao.maxRevision() + 1
            val nonce = crypto.randomBytes(NONCE_BYTES)
            val aad = recordAad(id.value, revision)
            val plaintext = RecordPayloadCodec.encode(account)
            val ciphertext = crypto.encryptRecord(KEY_VERSION, nonce, aad, plaintext)
            dao.upsert(
                AccountEntity(
                    recordId = id.value,
                    schemaVersion = SCHEMA_VERSION,
                    keyVersion = KEY_VERSION,
                    revision = revision,
                    nonce = nonce,
                    ciphertext = ciphertext,
                ),
            )
            OperationResult.Success(account.summary)
        } catch (e: Exception) {
            OperationResult.Failure(RepositoryError.StorageFailure)
        }
    }

    override suspend fun delete(id: AccountId, lease: AccessLease): OperationResult<Unit, RepositoryError> {
        if (!gate.isValid(lease, OperationBinding(OperationType.Write, setOf(id)))) {
            return OperationResult.Failure(RepositoryError.Unauthorized)
        }
        return try {
            val existing = dao.findById(id.value)
                ?: return OperationResult.Failure(RepositoryError.NotFound)
            dao.deleteById(existing.recordId)
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            OperationResult.Failure(RepositoryError.StorageFailure)
        }
    }

    override suspend fun snapshot(ids: Set<AccountId>, lease: AccessLease): OperationResult<List<SensitiveAccount>, RepositoryError> {
        if (!gate.isValid(lease, OperationBinding(OperationType.Read, ids))) {
            return OperationResult.Failure(RepositoryError.Unauthorized)
        }
        return try {
            val accounts = ids.map { id ->
                val entity = dao.findById(id.value)
                    ?: return OperationResult.Failure(RepositoryError.NotFound)
                decryptEntity(entity)
            }
            OperationResult.Success(accounts)
        } catch (e: Exception) {
            OperationResult.Failure(RepositoryError.StorageFailure)
        }
    }

    private suspend fun decryptEntity(entity: AccountEntity): SensitiveAccount {
        require(entity.keyVersion == KEY_VERSION) { "Unsupported record key version: ${entity.keyVersion}" }
        val aad = recordAad(entity.recordId, entity.revision)
        val plaintext = crypto.decryptRecord(KEY_VERSION, entity.nonce, aad, entity.ciphertext)
        return RecordPayloadCodec.decode(AccountId(entity.recordId), plaintext)
    }

    private fun recordAad(recordId: String, revision: Long): ByteArray =
        "authenticator-record:v1|$recordId|$KEY_VERSION|$revision".toByteArray(Charsets.US_ASCII)

    private companion object {
        const val SCHEMA_VERSION = 1
        const val KEY_VERSION = 2
        const val NONCE_BYTES = 12
    }
}
