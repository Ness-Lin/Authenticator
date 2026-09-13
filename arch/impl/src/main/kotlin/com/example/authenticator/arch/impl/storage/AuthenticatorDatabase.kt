package com.example.authenticator.arch.impl.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
internal abstract class AuthenticatorDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}