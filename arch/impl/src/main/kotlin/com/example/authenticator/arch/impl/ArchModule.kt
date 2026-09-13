package com.example.authenticator.arch.impl

import android.content.Context
import androidx.room.Room
import com.example.authenticator.arch.api.AccessGate
import com.example.authenticator.arch.api.AccountRepository
import com.example.authenticator.arch.api.Clock
import com.example.authenticator.arch.api.CryptoProvider
import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.arch.impl.crypto.KeystoreCryptoProvider
import com.example.authenticator.arch.impl.encoding.DefaultEncodingCodec
import com.example.authenticator.arch.impl.security.DefaultAccessGate
import com.example.authenticator.arch.impl.storage.AuthenticatorDatabase
import com.example.authenticator.arch.impl.storage.RoomAccountRepository

/**
 * arch 实现层装配工厂。仅此入口暴露实现，返回 arch:api 契约；具体实现保持 internal。
 */
object ArchModule {
    fun createAccessGate(): AccessGate = DefaultAccessGate()
    fun createCryptoProvider(): CryptoProvider = KeystoreCryptoProvider()
    fun createClock(): Clock = SystemClock()
    fun createEncodingCodec(): EncodingCodec = DefaultEncodingCodec()

    fun createAccountRepository(context: Context, crypto: CryptoProvider, gate: AccessGate): AccountRepository {
        val db = Room.databaseBuilder(
            context.applicationContext,
            AuthenticatorDatabase::class.java,
            "authenticator.db",
        ).build()
        return RoomAccountRepository(db.accountDao(), crypto, gate)
    }
}