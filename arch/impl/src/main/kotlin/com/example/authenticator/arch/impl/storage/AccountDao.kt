package com.example.authenticator.arch.impl.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AccountDao {
    @Query("SELECT * FROM encrypted_accounts ORDER BY record_id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM encrypted_accounts WHERE record_id = :recordId LIMIT 1")
    suspend fun findById(recordId: String): AccountEntity?

    @Query("DELETE FROM encrypted_accounts WHERE record_id = :recordId")
    suspend fun deleteById(recordId: String)

    @Query("SELECT COALESCE(MAX(revision), 0) FROM encrypted_accounts")
    suspend fun maxRevision(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountEntity)
}