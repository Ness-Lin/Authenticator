package com.example.authenticator.feature.security.impl

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.feature.security.api.SecuritySession

object SecurityModule {
    fun createSession(gate: AccessGate): SecuritySession = DefaultSecuritySession(gate)
}
