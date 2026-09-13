package com.example.authenticator.feature.token.api

import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.AccountSummary
import kotlinx.coroutines.flow.Flow

data class TokenValue(val accountId: AccountId, val code: String, val validFromMillis: Long, val validUntilMillis: Long) {
    override fun toString(): String = "TokenValue([REDACTED])"
}
interface TokenService { suspend fun generate(accountId: AccountId): TokenResult; fun observeAccounts(query: String = ""): Flow<List<AccountSummary>> }
sealed interface TokenResult { data class Success(val value: TokenValue) : TokenResult; data object Locked : TokenResult; data object NotFound : TokenResult; data object InvalidTime : TokenResult; data object Failed : TokenResult }
