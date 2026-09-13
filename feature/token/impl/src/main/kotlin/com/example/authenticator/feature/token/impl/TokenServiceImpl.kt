package com.example.authenticator.feature.token.impl

import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.AccountSummary
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.RepositoryError
import com.example.authenticator.common.basic.OperationResult
import com.example.authenticator.common.basic.SensitiveBytes
import com.example.authenticator.feature.security.api.SecuritySession
import com.example.authenticator.feature.token.api.TokenResult
import com.example.authenticator.feature.token.api.TokenService
import com.example.authenticator.feature.token.api.TokenValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class TokenServiceImpl(
    private val repository: AccountRepository,
    private val crypto: CryptoProvider,
    private val clock: Clock,
    private val session: SecuritySession,
) : TokenService {

    override suspend fun generate(accountId: AccountId): TokenResult {
        val lease = session.currentLease() ?: return TokenResult.Locked
        return when (val result = repository.get(accountId, lease)) {
            is OperationResult.Failure -> when (result.error) {
                RepositoryError.Unauthorized -> TokenResult.Locked
                RepositoryError.NotFound -> TokenResult.NotFound
                else -> TokenResult.Failed
            }
            is OperationResult.Success -> {
                val account = result.value
                try {
                    val secret = account.secret.copy()
                    val output = TotpEngine.generate(
                        secret = secret,
                        algorithm = account.parameters.algorithm,
                        digits = account.parameters.digits,
                        periodSeconds = account.parameters.periodSeconds,
                        epochMillis = clock.epochMillis(),
                    ) { algorithm, s, message -> crypto.hmac(algorithm, SensitiveBytes(s), message) }
                    TokenResult.Success(
                        TokenValue(
                            accountId = accountId,
                            code = output.code,
                            validFromMillis = output.validFromMillis,
                            validUntilMillis = output.validUntilMillis,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    TokenResult.Failed
                }
            }
        }
    }

    override fun observeAccounts(query: String): Flow<List<AccountSummary>> {
        val lease = session.currentLease() ?: return flowOf(emptyList())
        val lowercase = query.trim().lowercase()
        return repository.observeSummaries(lease).map { list ->
            if (lowercase.isEmpty()) {
                list
            } else {
                list.filter {
                    it.issuer.lowercase().contains(lowercase) || it.accountName.lowercase().contains(lowercase)
                }
            }
        }
    }
}