package com.example.authenticator.wiring

import com.example.authenticator.feature.enrollment.api.EnrollmentService
import com.example.authenticator.feature.security.api.SecuritySession
import com.example.authenticator.feature.token.api.TokenService
import com.example.authenticator.feature.transfer.api.ExportService

internal class AppGraph(
    val tokenService: TokenService,
    val enrollmentService: EnrollmentService,
    val exportService: ExportService,
    val securitySession: SecuritySession,
)

internal fun createAppGraph(context: android.content.Context): AppGraph {
    val gate = com.example.authenticator.arch.impl.ArchModule.createAccessGate()
    val crypto = com.example.authenticator.arch.impl.ArchModule.createCryptoProvider()
    val clock = com.example.authenticator.arch.impl.ArchModule.createClock()
    val codec = com.example.authenticator.arch.impl.ArchModule.createEncodingCodec()
    val repository = com.example.authenticator.arch.impl.ArchModule.createAccountRepository(context, crypto, gate)
    val security = com.example.authenticator.feature.security.impl.SecurityModule.createSession(gate)
    val token = com.example.authenticator.feature.token.impl.TokenModule.createTokenService(repository, crypto, clock, security)
    val enrollment = com.example.authenticator.feature.enrollment.impl.EnrollmentModule.createService(repository, security, clock)
    val encoder = com.example.authenticator.feature.token.impl.TokenModule.createOtpUriEncoder(codec)
    val export = com.example.authenticator.feature.transfer.impl.TransferModule.createExportService(repository, crypto, codec, encoder, gate, clock)
    return AppGraph(token, enrollment, export, security)
}
