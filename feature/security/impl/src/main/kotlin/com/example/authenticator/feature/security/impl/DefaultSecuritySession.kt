package com.example.authenticator.feature.security.impl

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccessLease
import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.OperationBinding
import com.example.authenticator.arch.api.OperationType
import com.example.authenticator.feature.security.api.AuthenticationResult
import com.example.authenticator.feature.security.api.AuthorizationResult
import com.example.authenticator.feature.security.api.SecuritySession
import com.example.authenticator.feature.security.api.SecuritySessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-scoped session boundary. The Android biometric prompt is hosted by the UI layer. */
internal class DefaultSecuritySession(private val gate: AccessGate) : SecuritySession {
    private val mutableState = MutableStateFlow(SecuritySessionState.Locked)
    private var lease: AccessLease? = null

    override val state: StateFlow<SecuritySessionState> = mutableState

    override suspend fun unlock(): AuthenticationResult {
        if (mutableState.value == SecuritySessionState.Unavailable) return AuthenticationResult.Unavailable
        mutableState.value = SecuritySessionState.Authenticating
        val issued = gate.issue(OperationBinding(OperationType.Write))
        lease = issued
        mutableState.value = SecuritySessionState.Unlocked
        return AuthenticationResult.Unlocked(issued)
    }

    override suspend fun authorizeExport(requestId: String, accountIds: Set<AccountId>): AuthorizationResult {
        val current = lease ?: return AuthorizationResult.Rejected
        if (mutableState.value != SecuritySessionState.Unlocked) return AuthorizationResult.Rejected
        val exportLease = gate.issue(OperationBinding(OperationType.Export, accountIds, requestId))
        gate.revoke(current)
        lease = exportLease
        return AuthorizationResult.Authorized(exportLease)
    }

    override fun currentLease(): AccessLease? = lease

    override fun lock() {
        lease?.let(gate::revoke)
        lease = null
        gate.revokeAll()
        mutableState.value = SecuritySessionState.Locked
    }
}
