package com.example.authenticator.arch.impl.security

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccessLease
import com.example.authenticator.arch.api.OperationBinding
import com.example.authenticator.arch.api.OperationType
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内访问门禁实现。
 *
 * lease 为不透明能力句柄，仅在本进程内存登记；门禁验证的是登记状态与授权范围，
 * 而不是相信调用方提供的任何字段。不提供跨进程或持久化能力，也不防御同进程恶意代码。
 */
internal class DefaultAccessGate : AccessGate {

    private data class Grant(val binding: OperationBinding)

    private val grants = ConcurrentHashMap<String, Grant>()
    private val random = SecureRandom()

    override fun issue(binding: OperationBinding): AccessLease {
        val token = randomToken()
        grants[token] = Grant(binding)
        return AccessLease(token)
    }

    override fun isValid(lease: AccessLease, binding: OperationBinding): Boolean {
        val grant = grants[lease.value] ?: return false
        return authorize(grant.binding, binding)
    }

    override fun revoke(lease: AccessLease) {
        grants.remove(lease.value)
    }

    override fun revokeAll() {
        grants.clear()
    }

    private fun authorize(granted: OperationBinding, requested: OperationBinding): Boolean {
        val operationAllowed = when (requested.operation) {
            OperationType.Export -> granted.operation == OperationType.Export
            OperationType.Read -> granted.operation == OperationType.Read || granted.operation == OperationType.Export
            OperationType.Write -> granted.operation == OperationType.Write
        }
        if (!operationAllowed) return false
        // 空集合表示“全部账户”，否则请求的账户必须是已授权账户的子集。
        if (granted.accountIds.isNotEmpty() && !granted.accountIds.containsAll(requested.accountIds)) return false
        // requestId 仅在导出这类绑定单次请求的授权上才要求严格匹配；普通读写绑定不携带 requestId。
        if (granted.operation == OperationType.Export && requested.operation == OperationType.Export) {
            if (granted.requestId != requested.requestId) return false
        }
        return true
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}