package com.example.authenticator.arch.impl.security

import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.arch.api.OperationBinding
import com.example.authenticator.arch.api.OperationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAccessGateTest {

    private val gate = DefaultAccessGate()

    @Test
    fun `issued lease validates against matching binding`() {
        val binding = OperationBinding(OperationType.Read)
        val lease = gate.issue(binding)
        assertTrue(gate.isValid(lease, OperationBinding(OperationType.Read)))
    }

    @Test
    fun `different operation is rejected`() {
        val lease = gate.issue(OperationBinding(OperationType.Read))
        assertFalse(gate.isValid(lease, OperationBinding(OperationType.Write)))
    }

    @Test
    fun `account subset outside granted scope is rejected`() {
        val a = AccountId("a")
        val lease = gate.issue(OperationBinding(OperationType.Read, setOf(a)))
        assertFalse(gate.isValid(lease, OperationBinding(OperationType.Read, setOf(AccountId("b")))))
    }

    @Test
    fun `empty grant scope allows any single account`() {
        val lease = gate.issue(OperationBinding(OperationType.Read, emptySet()))
        assertTrue(gate.isValid(lease, OperationBinding(OperationType.Read, setOf(AccountId("any")))))
    }

    @Test
    fun `request id binding must match`() {
        val lease = gate.issue(OperationBinding(OperationType.Export, emptySet(), requestId = "req-1"))
        assertFalse(gate.isValid(lease, OperationBinding(OperationType.Export, emptySet(), requestId = "req-2")))
        assertTrue(gate.isValid(lease, OperationBinding(OperationType.Export, emptySet(), requestId = "req-1")))
    }

    @Test
    fun `revoked lease is rejected`() {
        val lease = gate.issue(OperationBinding(OperationType.Read))
        gate.revoke(lease)
        assertFalse(gate.isValid(lease, OperationBinding(OperationType.Read)))
    }

    @Test
    fun `revokeAll invalidates every lease`() {
        val lease = gate.issue(OperationBinding(OperationType.Read))
        gate.revokeAll()
        assertFalse(gate.isValid(lease, OperationBinding(OperationType.Read)))
    }
}