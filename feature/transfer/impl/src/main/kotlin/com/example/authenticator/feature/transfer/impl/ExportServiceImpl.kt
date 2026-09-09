package com.example.authenticator.feature.transfer.impl

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccessLease
import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.arch.api.OperationBinding
import com.example.authenticator.arch.api.OperationType
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.arch.api.RepositoryError
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.common.basic.OperationResult
import com.example.authenticator.common.basic.PasswordBytes
import com.example.authenticator.feature.token.api.OtpUriEncoder
import com.example.authenticator.feature.transfer.api.ExportFormat
import com.example.authenticator.feature.transfer.api.ExportRequest
import com.example.authenticator.feature.transfer.api.ExportResult
import com.example.authenticator.feature.transfer.api.ExportService
import com.example.authenticator.feature.transfer.api.OutputTarget
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 导出用例实现。
 *
 * 加密格式遵循需求文档 4.3 的 v1 信封：AES-256-GCM + PBKDF2-HMAC-SHA256，
 * 明文格式为逐行 otpauth URI。执行前校验导出租约，快照读取并在作用域内解密。
 */
internal class ExportServiceImpl(
    private val repository: AccountRepository,
    private val crypto: CryptoProvider,
    private val codec: EncodingCodec,
    private val uriEncoder: OtpUriEncoder,
    private val gate: AccessGate,
    private val clock: Clock,
) : ExportService {

    private val json = Json { encodeDefaults = true }

    override suspend fun execute(
        request: ExportRequest,
        target: OutputTarget,
        lease: AccessLease,
        password: PasswordBytes?,
    ): ExportResult {
        if (!isValidRequest(request, password)) return ExportResult.InvalidRequest
        if (!gate.isValid(lease, OperationBinding(OperationType.Export, request.accountIds, request.requestId))) {
            return ExportResult.Locked
        }

        val accounts = when (val snapshot = repository.snapshot(request.accountIds, lease)) {
            is OperationResult.Failure -> return snapshot.error.toExportResult()
            is OperationResult.Success -> snapshot.value
        }

        return try {
            val content = when (request.format) {
                ExportFormat.EncryptedJson -> buildEncrypted(accounts, requireNotNull(password))
                ExportFormat.PlaintextUri -> buildPlaintext(accounts)
            }
            if (content.size > MAX_EXPORT_BYTES) return ExportResult.Failed
            writeTarget(target, content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExportResult.Failed
        } finally {
            accounts.forEach { it.secret.clear() }
            password?.clear()
        }
    }

    private suspend fun buildEncrypted(accounts: List<SensitiveAccount>, password: PasswordBytes): ByteArray {
        val payload = buildPayload(accounts)
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val salt = crypto.randomBytes(SALT_BYTES)
        val nonce = crypto.randomBytes(NONCE_BYTES)
        val aad = AAD_ASCII.toByteArray(Charsets.US_ASCII)
        val key = crypto.deriveExportKey(password, salt)
        val ciphertext = try {
            crypto.encryptExport(key, nonce, aad, payloadBytes)
        } finally {
            key.clear()
        }
        val envelope = BackupEnvelope(
            format = "authenticator-backup",
            version = 1,
            kdf = BackupKdf(
                name = KDF_NAME,
                iterations = KDF_ITERATIONS,
                salt = codec.base64Encode(salt),
            ),
            cipher = BackupCipher(
                name = CIPHER_NAME,
                nonce = codec.base64Encode(nonce),
                tagBits = TAG_BITS,
            ),
            ciphertext = codec.base64Encode(ciphertext),
        )
        return json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
    }

    private fun buildPlaintext(accounts: List<SensitiveAccount>): ByteArray {
        val sb = StringBuilder()
        accounts.forEach { account ->
            sb.append(uriEncoder.encode(account)).append('\n')
        }
        // 确保末尾固定保留一个 LF。
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildPayload(accounts: List<SensitiveAccount>): BackupPayload {
        val now = clock.epochMillis()
        return BackupPayload(
            schemaVersion = 1,
            exportedAt = UtcTimeFormatter.format(now),
            accounts = accounts.map { account ->
                val secretB32 = codec.base32EncodeNoPadding(account.secret.copy())
                BackupAccount(
                    id = account.summary.id.value,
                    issuer = account.summary.issuer,
                    accountName = account.summary.accountName,
                    type = "totp",
                    secret = secretB32,
                    algorithm = account.parameters.algorithm.name,
                    digits = account.parameters.digits,
                    period = account.parameters.periodSeconds,
                    createdAt = UtcTimeFormatter.format(account.summary.createdAtEpochMillis),
                )
            },
        )
    }

    private suspend fun writeTarget(target: OutputTarget, content: ByteArray): ExportResult {
        val session = try {
            target.open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ExportResult.Failed
        }
        return try {
            session.write(content)
            session.finish()
            ExportResult.Completed
        } catch (e: CancellationException) {
            session.abort()
            throw e
        } catch (e: Exception) {
            session.abort()
            ExportResult.Failed
        }
    }

    private fun isValidRequest(request: ExportRequest, password: PasswordBytes?): Boolean {
        if (request.accountIds.isEmpty() || request.accountIds.size > MAX_ACCOUNTS) return false
        return when (request.format) {
            ExportFormat.EncryptedJson -> password != null
            ExportFormat.PlaintextUri -> request.riskConfirmed && password == null
        }
    }

    private fun RepositoryError.toExportResult(): ExportResult = when (this) {
        RepositoryError.Unauthorized -> ExportResult.Locked
        RepositoryError.NotFound -> ExportResult.InvalidRequest
        else -> ExportResult.Failed
    }

    private companion object {
        const val KDF_NAME = "PBKDF2-HMAC-SHA256"
        const val KDF_ITERATIONS = 600_000
        const val CIPHER_NAME = "AES-256-GCM"
        const val AAD_ASCII = "authenticator-backup:v1"
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val MAX_ACCOUNTS = 1000
        const val MAX_EXPORT_BYTES = 10 * 1024 * 1024
    }
}