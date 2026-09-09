package com.example.authenticator.feature.transfer.impl

import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.feature.token.api.OtpUriEncoder
import com.example.authenticator.feature.transfer.api.ExportService

/** transfer 实现层装配工厂；仅返回 api 契约。 */
object TransferModule {
    fun createExportService(
        repository: AccountRepository,
        crypto: CryptoProvider,
        codec: EncodingCodec,
        uriEncoder: OtpUriEncoder,
        gate: AccessGate,
        clock: Clock,
    ): ExportService = ExportServiceImpl(repository, crypto, codec, uriEncoder, gate, clock)
}