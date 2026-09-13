package com.example.authenticator.feature.security.api

import com.example.authenticator.arch.api.AccessLease
import kotlinx.coroutines.flow.StateFlow

enum class SecuritySessionState { Locked, Authenticating, Unlocked, Unavailable }
interface SecuritySession {
    val state: StateFlow<SecuritySessionState>
    suspend fun unlock(): AuthenticationResult
    suspend fun authorizeExport(requestId: String, accountIds: Set<com.example.authenticator.arch.api.AccountId>): AuthorizationResult
    fun currentLease(): AccessLease?
    fun lock()
}
sealed interface AuthenticationResult { data class Unlocked(val lease: AccessLease) : AuthenticationResult; data object Rejected : AuthenticationResult; data object Unavailable : AuthenticationResult }
sealed interface AuthorizationResult { data class Authorized(val lease: AccessLease) : AuthorizationResult; data object Rejected : AuthorizationResult; data object Unavailable : AuthorizationResult }
