package com.example.authenticator.feature.enrollment.impl

import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.arch.api.OtpParameters
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.common.basic.OperationResult
import com.example.authenticator.common.basic.SensitiveBytes
import com.example.authenticator.feature.enrollment.api.ConfirmEnrollment
import com.example.authenticator.feature.enrollment.api.EnrollmentDraftId
import com.example.authenticator.feature.enrollment.api.EnrollmentResult
import com.example.authenticator.feature.enrollment.api.EnrollmentService
import com.example.authenticator.feature.enrollment.api.ManualEnrollmentRequest
import com.example.authenticator.feature.enrollment.api.Preparation
import com.example.authenticator.feature.enrollment.api.PreparationResult
import com.example.authenticator.feature.enrollment.api.UriEnrollmentRequest
import com.example.authenticator.feature.security.api.SecuritySession
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class EnrollmentServiceImpl(
    private val repository: AccountRepository,
    private val session: SecuritySession,
    private val clock: Clock,
) : EnrollmentService {
    private data class Draft(val account: SensitiveAccount, val revision: Long)
    private val drafts = ConcurrentHashMap<String, Draft>()

    override suspend fun prepareManual(request: ManualEnrollmentRequest): PreparationResult {
        val normalized = normalize(request.issuer, request.accountName, request.secret, request.algorithm, request.digits, request.periodSeconds)
            ?: return PreparationResult.Invalid
        return prepare(normalized)
    }

    override suspend fun prepareQr(request: UriEnrollmentRequest): PreparationResult {
        if (request.uri.toByteArray(StandardCharsets.UTF_8).size > 8192) return PreparationResult.Invalid
        val parsed = parseUri(request.uri) ?: return PreparationResult.Invalid
        return prepare(parsed)
    }

    override suspend fun confirm(request: ConfirmEnrollment): EnrollmentResult {
        val draft = drafts[request.draftId.value] ?: return EnrollmentResult.DraftExpired
        if (draft.revision != request.revision) return EnrollmentResult.DraftExpired
        if (draft.account.secret.copy().size < 16 && !request.weakSecretConfirmed) return EnrollmentResult.Invalid
        val lease = session.currentLease() ?: return EnrollmentResult.Locked
        return when (val result = repository.save(draft.account, lease)) {
            is OperationResult.Success -> { drafts.remove(request.draftId.value); EnrollmentResult.Saved }
            is OperationResult.Failure -> when (result.error) {
                com.example.authenticator.arch.api.RepositoryError.Conflict -> EnrollmentResult.DuplicateConfirmationRequired
                com.example.authenticator.arch.api.RepositoryError.Unauthorized -> EnrollmentResult.Locked
                else -> EnrollmentResult.Failed
            }
        }
    }

    override fun discard(draftId: EnrollmentDraftId) { drafts.remove(draftId.value)?.account?.secret?.clear() }

    private suspend fun prepare(values: Values): PreparationResult {
        val lease = session.currentLease() ?: return PreparationResult.Locked
        val id = AccountId(UUID.randomUUID().toString())
        val account = SensitiveAccount(
            AccountSummary(id, values.issuer, values.accountName, clock.epochMillis()),
            OtpParameters(values.algorithm, values.digits, values.period),
            SensitiveBytes(values.secret),
        )
        val draftId = EnrollmentDraftId(UUID.randomUUID().toString())
        drafts[draftId.value] = Draft(account, 1L)
        return PreparationResult.Ready(Preparation(draftId, 1L, values.accountName, values.issuer, values.algorithm.name, values.digits, values.period))
    }

    private data class Values(val issuer: String, val accountName: String, val secret: ByteArray, val algorithm: OtpAlgorithm, val digits: Int, val period: Int)

    private fun normalize(issuerRaw: String, accountRaw: String, secretRaw: String, algorithmRaw: String, digits: Int, period: Int): Values? {
        val issuer = issuerRaw.trim()
        val accountName = accountRaw.trim()
        if (accountName.isEmpty() || issuer.codePointCount(0, issuer.length) > 128 || accountName.codePointCount(0, accountName.length) > 256) return null
        val algorithm = when (algorithmRaw.uppercase()) { "SHA1" -> OtpAlgorithm.SHA1; "SHA256" -> OtpAlgorithm.SHA256; "SHA512" -> OtpAlgorithm.SHA512; else -> return null }
        if (digits !in setOf(6, 8) || period !in 15..120) return null
        val compact = secretRaw.filterNot { it == ' ' || it == '-' }.uppercase()
        val secret = decodeBase32(compact) ?: return null
        if (secret.size !in 10..128) return null
        return Values(issuer, accountName, secret, algorithm, digits, period)
    }

    private fun parseUri(raw: String): Values? {
        return try {
            val uri = URI(raw)
            if (uri.scheme != "otpauth" || uri.host?.lowercase() != "totp" || uri.rawQuery == null) return null
            val pairs = linkedMapOf<String, String>()
            uri.rawQuery.split('&').forEach { item ->
                val index = item.indexOf('=')
                if (index <= 0) return null
                val key = item.substring(0, index)
                if (pairs.put(key, decode(item.substring(index + 1))) != null) return null
            }
            val label = decode(uri.rawPath.removePrefix("/"))
            if (label.isEmpty()) return null
            val colon = label.indexOf(':')
            val labelIssuer = if (colon >= 0) label.substring(0, colon) else ""
            val name = if (colon >= 0) label.substring(colon + 1) else label
            val issuer = pairs["issuer"] ?: labelIssuer
            if (labelIssuer.isNotEmpty() && pairs["issuer"] != null && pairs["issuer"] != labelIssuer) return null
            normalize(issuer, name, pairs["secret"] ?: return null, pairs["algorithm"] ?: "SHA1", pairs["digits"]?.toIntOrNull() ?: 6, pairs["period"]?.toIntOrNull() ?: 30)
        } catch (_: Exception) { null }
    }

    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun decodeBase32(value: String): ByteArray? {
        if (value.isEmpty() || !value.matches(Regex("[A-Z2-7]+=*"))) return null
        val firstPad = value.indexOf('=')
        if (firstPad >= 0 && value.substring(firstPad).any { it != '=' }) return null
        val core = value.substringBefore('=')
        if (core.length % 8 in setOf(1, 3, 6)) return null
        var buffer = 0; var bits = 0; val out = ArrayList<Byte>()
        core.forEach { c -> buffer = (buffer shl 5) or "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c); bits += 5; if (bits >= 8) { bits -= 8; out += ((buffer shr bits) and 0xff).toByte() } }
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray()
    }
}
