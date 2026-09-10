package com.example.authenticator.feature.enrollment.impl

import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.feature.enrollment.api.EnrollmentService
import com.example.authenticator.feature.security.api.SecuritySession

object EnrollmentModule {
    fun createService(repository: AccountRepository, session: SecuritySession, clock: Clock): EnrollmentService = EnrollmentServiceImpl(repository, session, clock)
}
