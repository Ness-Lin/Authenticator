package com.example.authenticator.arch.impl.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.common.basic.SensitiveBytes
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.KeyParameter

/**
 * 基于 Android Keystore、JCA 与 Bouncy Castle 的加密能力实现。
 *
 * - 本机记录加密使用 AndroidKeyStore 中不可导出的 AES 密钥（AES/GCM/NoPadding）。
 * - 导出 KDF 使用 Bouncy Castle 轻量级 PBKDF2-HMAC-SHA256，显式以 UTF-8 字节为口令输入，
 *   避免 API 24–25 平台算法可用性与字符转换差异；不注册全局 Provider，仅使用轻量级 API。
 * - 导出加密使用 JCA AES-256-GCM，与设备 Keystore 解耦。
 */
internal class KeystoreCryptoProvider : CryptoProvider {

    private val androidKeyStore = "AndroidKeyStore"

    override suspend fun hmac(algorithm: OtpAlgorithm, secret: SensitiveBytes, message: ByteArray): ByteArray {
        val macName = when (algorithm) {
            OtpAlgorithm.SHA1 -> "HmacSHA1"
            OtpAlgorithm.SHA256 -> "HmacSHA256"
            OtpAlgorithm.SHA512 -> "HmacSHA512"
        }
        val keyBytes = secret.copy()
        return try {
            val mac = Mac.getInstance(macName)
            mac.init(SecretKeySpec(keyBytes, macName))
            mac.doFinal(message)
        } finally {
            keyBytes.fill(0)
        }
    }

    override suspend fun deriveExportKey(password: SensitiveBytes, salt: ByteArray): SensitiveBytes {
        val passwordBytes = password.copy()
        return try {
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(passwordBytes, salt, ExportConstants.EXPORT_ITERATIONS)
            val key = generator.generateDerivedParameters(ExportConstants.EXPORT_KEY_BITS) as KeyParameter
            SensitiveBytes(key.key)
        } finally {
            passwordBytes.fill(0)
        }
    }

    override suspend fun encryptExport(key: SensitiveBytes, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val keyBytes = key.copy()
        return try {
            aesGcm(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), nonce, aad, plaintext)
        } finally {
            keyBytes.fill(0)
        }
    }

    override suspend fun encryptRecord(keyVersion: Int, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val key = getOrCreateRecordKey(keyVersion)
        return aesGcm(Cipher.ENCRYPT_MODE, key, nonce, aad, plaintext)
    }

    override suspend fun decryptRecord(keyVersion: Int, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = getRecordKey(keyVersion)
        return aesGcm(Cipher.DECRYPT_MODE, key, nonce, aad, ciphertext)
    }

    override suspend fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun aesGcm(mode: Int, key: java.security.Key, nonce: ByteArray, aad: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }

    private fun getOrCreateRecordKey(keyVersion: Int): java.security.Key {
        val alias = recordAlias(keyVersion)
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        (keyStore.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getRecordKey(keyVersion: Int): java.security.Key {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        return keyStore.getKey(recordAlias(keyVersion), null)
            ?: error("Record key not present for version $keyVersion")
    }

    private fun recordAlias(keyVersion: Int): String = "authenticator-record-key-v$keyVersion"

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}