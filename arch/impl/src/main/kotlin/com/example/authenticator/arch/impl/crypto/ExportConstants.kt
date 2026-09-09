package com.example.authenticator.arch.impl.crypto

/** 导出 KDF 的固定 v1 参数，由 arch 实现持有并与导出格式 v1 的声明保持一致。 */
internal object ExportConstants {
    const val EXPORT_ITERATIONS = 600_000
    const val EXPORT_KEY_BITS = 256
}