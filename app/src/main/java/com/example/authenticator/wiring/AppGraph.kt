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
