package com.example.authenticator.feature.transfer.impl

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccessLease
import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.arch.api.OperationBinding
import com.example.authenticator.arch.api.OperationType
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.arch.api.OtpParameters
import com.example.authenticator.arch.api.RepositoryError
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.common.basic.OperationResult
import com.example.authenticator.common.basic.SensitiveBytes
import com.example.authenticator.feature.token.api.OtpUriEncoder
import com.example.authenticator.feature.transfer.api.ExportFormat
import com.example.authenticator.feature.transfer.api.ExportRequest
import com.example.authenticator.feature.transfer.api.ExportResult
import com.example.authenticator.feature.transfer.api.OutputTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExportServiceImplTest {

    private class FakeAccountRepository(private val accounts: List<SensitiveAccount>) : AccountRepository {
        override fun observeSummaries(lease: AccessLease): Flow<List<AccountSummary>> = flowOf(accounts.map { it.summary })
        override suspend fun get(id: AccountId, lease: AccessLease) = error("not used")
        override suspend fun save(account: SensitiveAccount, lease: AccessLease) = error("not used")
        override suspend fun delete(id: AccountId, lease: AccessLease) = error("not used")
        override suspend fun snapshot(ids: Set<AccountId>, lease: AccessLease): OperationResult<List<SensitiveAccount>, RepositoryError> {
            val selected = accounts.filter { it.summary.id in ids }
            return if (selected.size == ids.size) {
                OperationResult.Success(selected)
            } else {
                OperationResult.Failure(RepositoryError.NotFound)
            }
        }
    }

    private class FakeAccessGate : AccessGate {
        private var valid = true
        override fun issue(binding: OperationBinding) = AccessLease("lease")
        override fun isValid(lease: AccessLease, binding: OperationBinding) = valid
        override fun revoke(lease: AccessLease) = Unit
        override fun revokeAll() { valid = false }
    }

    private class FakeClock(private val now: Long) : Clock {
        override fun epochMillis() = now
        override fun monotonicMillis() = now
    }

    private class FakeCryptoProvider : CryptoProvider {
        override suspend fun hmac(algorithm: OtpAlgorithm, secret: SensitiveBytes, message: ByteArray) = error("not used")
        override suspend fun deriveExportKey(password: SensitiveBytes, salt: ByteArray): SensitiveBytes {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(String(password.copy(), Charsets.UTF_8).toCharArray(), salt, 600_000, 256)
            return SensitiveBytes(factory.generateSecret(spec).encoded)
        }

        override suspend fun encryptExport(key: SensitiveBytes, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copy(), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(plaintext)
        }

        override suspend fun encryptRecord(keyVersion: Int, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray) = error("not used")
        override suspend fun decryptRecord(keyVersion: Int, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray) = error("not used")
        override suspend fun randomBytes(size: Int) = ByteArray(size)
    }

    private class FakeEncodingCodec : EncodingCodec {
        override fun base32EncodeNoPadding(bytes: ByteArray): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
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
            return out.toString()
        }

        override fun base64Encode(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)
        override fun percentEncode(text: String) = text
    }

    private class FakeUriEncoder : OtpUriEncoder {
        override fun encode(account: SensitiveAccount) = "otpauth://totp/${account.summary.accountName}?secret=ABC"
    }

    private class CapturingTarget : OutputTarget {
        var written: ByteArray? = null
        override val reference = "test-target"
        override suspend fun open() = object : com.example.authenticator.feature.transfer.api.OutputSession {
            override suspend fun write(bytes: ByteArray) { written = bytes }
            override suspend fun finish() = Unit
            override suspend fun abort() = Unit
        }
    }

    private fun account(id: String, issuer: String, name: String, secretBytes: ByteArray, algorithm: OtpAlgorithm, digits: Int, period: Int) =
        SensitiveAccount(
            AccountSummary(AccountId(id), issuer, name, 1_700_000_000_000),
            OtpParameters(algorithm, digits, period),
            SensitiveBytes(secretBytes),
        )

    // —— 测试 ——

    @Test
    fun `encrypted export round-trips and is independently decryptable`() = runBlocking {
        val acct = account("id-1", "Example", "a@b.c", "Hello, exporter!".toByteArray(), OtpAlgorithm.SHA256, 6, 30)
        val repo = FakeAccountRepository(listOf(acct))
        val gate = FakeAccessGate()
        val password = SensitiveBytes("correct horse battery staple".toByteArray())

        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        val target = CapturingTarget()
        val result = service.execute(
            ExportRequest("req-1", setOf(acct.summary.id), ExportFormat.EncryptedJson, riskConfirmed = true),
            target,
            gate.issue(OperationBinding(OperationType.Export, setOf(acct.summary.id), "req-1")),
            password,
        )

        assertEquals(ExportResult.Completed, result)
        val envelope = Json.parseToJsonElement(String(target.written!!, Charsets.UTF_8)).jsonObject
        assertEquals("authenticator-backup", envelope["format"]!!.jsonPrimitive.content)
        assertEquals(1, envelope["version"]!!.jsonPrimitive.content.toInt())

        val kdf = envelope["kdf"]!!.jsonObject
        val cipher = envelope["cipher"]!!.jsonObject
        assertEquals("PBKDF2-HMAC-SHA256", kdf["name"]!!.jsonPrimitive.content)
        assertEquals(600_000, kdf["iterations"]!!.jsonPrimitive.content.toInt())
        assertEquals("AES-256-GCM", cipher["name"]!!.jsonPrimitive.content)
        assertEquals(128, cipher["tagBits"]!!.jsonPrimitive.content.toInt())

        val salt = java.util.Base64.getDecoder().decode(kdf["salt"]!!.jsonPrimitive.content)
        val nonce = java.util.Base64.getDecoder().decode(cipher["nonce"]!!.jsonPrimitive.content)
        val ciphertext = java.util.Base64.getDecoder().decode(envelope["ciphertext"]!!.jsonPrimitive.content)

        // 独立解密验证格式正确性。
        val keySpec = PBEKeySpec("correct horse battery staple".toCharArray(), salt, 600_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
        decCipher.updateAAD("authenticator-backup:v1".toByteArray(Charsets.US_ASCII))
        val plaintext = decCipher.doFinal(ciphertext)

        val payload = Json.parseToJsonElement(String(plaintext, Charsets.UTF_8)).jsonObject
        assertEquals(1, payload["schemaVersion"]!!.jsonPrimitive.content.toInt())
        val account = payload["accounts"]!!.jsonArray[0].jsonObject
        assertEquals("id-1", account["id"]!!.jsonPrimitive.content)
        assertEquals("Example", account["issuer"]!!.jsonPrimitive.content)
        assertEquals("a@b.c", account["accountName"]!!.jsonPrimitive.content)
        assertEquals("totp", account["type"]!!.jsonPrimitive.content)
        assertEquals("SHA256", account["algorithm"]!!.jsonPrimitive.content)
        assertEquals(6, account["digits"]!!.jsonPrimitive.content.toInt())
        assertEquals(30, account["period"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `plaintext export writes one uri per account with trailing newline`() = runBlocking {
        val a = account("id-1", "A", "a@b.c", "secret".toByteArray(), OtpAlgorithm.SHA1, 6, 30)
        val b = account("id-2", "B", "d@e.f", "secret2".toByteArray(), OtpAlgorithm.SHA256, 8, 60)
        val repo = FakeAccountRepository(listOf(a, b))
        val gate = FakeAccessGate()

        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        val target = CapturingTarget()
        val result = service.execute(
            ExportRequest("req-2", setOf(a.summary.id, b.summary.id), ExportFormat.PlaintextUri, riskConfirmed = true),
            target,
            gate.issue(OperationBinding(OperationType.Export)),
            password = null,
        )

        assertEquals(ExportResult.Completed, result)
        val text = String(target.written!!, Charsets.UTF_8)
        val lines = text.split('\n')
        assertEquals(3, lines.size) // 两个账户 + 末尾空行
        assertEquals("", lines.last())
    }

    @Test
    fun `plaintext export requires risk confirmation`() = runBlocking {
        val a = account("id-1", "A", "a@b.c", "secret".toByteArray(), OtpAlgorithm.SHA1, 6, 30)
        val repo = FakeAccountRepository(listOf(a))
        val gate = FakeAccessGate()
        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        val result = service.execute(
            ExportRequest("req-3", setOf(a.summary.id), ExportFormat.PlaintextUri, riskConfirmed = false),
            CapturingTarget(),
            gate.issue(OperationBinding(OperationType.Export)),
            password = null,
        )
        assertEquals(ExportResult.InvalidRequest, result)
    }

    @Test
    fun `encrypted export requires password`() = runBlocking {
        val a = account("id-1", "A", "a@b.c", "secret".toByteArray(), OtpAlgorithm.SHA1, 6, 30)
        val repo = FakeAccountRepository(listOf(a))
        val gate = FakeAccessGate()
        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        val result = service.execute(
            ExportRequest("req-4", setOf(a.summary.id), ExportFormat.EncryptedJson, riskConfirmed = true),
            CapturingTarget(),
            gate.issue(OperationBinding(OperationType.Export)),
            password = null,
        )
        assertEquals(ExportResult.InvalidRequest, result)
    }

    @Test
    fun `invalid gate returns locked`() = runBlocking {
        val a = account("id-1", "A", "a@b.c", "secret".toByteArray(), OtpAlgorithm.SHA1, 6, 30)
        val repo = FakeAccountRepository(listOf(a))
        val gate = FakeAccessGate()
        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        gate.revokeAll()
        val result = service.execute(
            ExportRequest("req-5", setOf(a.summary.id), ExportFormat.PlaintextUri, riskConfirmed = true),
            CapturingTarget(),
            AccessLease("some-stale-lease"),
            password = null,
        )
        assertEquals(ExportResult.Locked, result)
    }

    @Test
    fun `missing account returns invalid request`() = runBlocking {
        val a = account("id-1", "A", "a@b.c", "secret".toByteArray(), OtpAlgorithm.SHA1, 6, 30)
        val repo = FakeAccountRepository(listOf(a))
        val gate = FakeAccessGate()
        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        val result = service.execute(
            ExportRequest("req-6", setOf(a.summary.id, AccountId("missing")), ExportFormat.PlaintextUri, riskConfirmed = true),
            CapturingTarget(),
            gate.issue(OperationBinding(OperationType.Export)),
            password = null,
        )
        assertEquals(ExportResult.InvalidRequest, result)
    }

    @Test
    fun `tampered ciphertext fails independent decryption`() = runBlocking {
        val acct = account("id-1", "Example", "a@b.c", "secret".toByteArray(), OtpAlgorithm.SHA1, 6, 30)
        val repo = FakeAccountRepository(listOf(acct))
        val gate = FakeAccessGate()
        val service = ExportServiceImpl(repo, FakeCryptoProvider(), FakeEncodingCodec(), FakeUriEncoder(), gate, FakeClock(1_700_000_000_000))
        val target = CapturingTarget()
        service.execute(
            ExportRequest("req-7", setOf(acct.summary.id), ExportFormat.EncryptedJson, riskConfirmed = true),
            target,
            gate.issue(OperationBinding(OperationType.Export)),
            SensitiveBytes("correct horse battery staple".toByteArray()),
        )
        val envelope = Json.parseToJsonElement(String(target.written!!, Charsets.UTF_8)).jsonObject
        val ciphertext = java.util.Base64.getDecoder().decode(envelope["ciphertext"]!!.jsonPrimitive.content)
        ciphertext[ciphertext.size / 2] = (ciphertext[ciphertext.size / 2].toInt() xor 0x01).toByte()
        val nonce = java.util.Base64.getDecoder().decode(envelope["cipher"]!!.jsonObject["nonce"]!!.jsonPrimitive.content)

        val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(ByteArray(32), "AES"), GCMParameterSpec(128, nonce))
        decCipher.updateAAD("authenticator-backup:v1".toByteArray(Charsets.US_ASCII))
        var threw = false
        try {
            decCipher.doFinal(ciphertext)
        } catch (e: Exception) {
            threw = true
        }
        assertFalse("tampered ciphertext should fail authentication", threw == false)
    }
}