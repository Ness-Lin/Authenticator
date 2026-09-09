package com.example.authenticator.feature.token.impl

import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.feature.security.api.SecuritySession
import com.example.authenticator.feature.token.api.OtpUriEncoder
import com.example.authenticator.feature.token.api.TokenService

/** token 实现层装配工厂；仅返回 api 契约。 */
object TokenModule {
    fun createTokenService(
        repository: AccountRepository,
        crypto: CryptoProvider,
        clock: Clock,
        session: SecuritySession,
    ): TokenService = TokenServiceImpl(repository, crypto, clock, session)

    fun createOtpUriEncoder(codec: EncodingCodec): OtpUriEncoder = DefaultOtpUriEncoder(codec)
}