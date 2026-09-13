package com.example.authenticator.arch.impl.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 密文账户表，不保存任何明文名称、发行方、密钥或算法元数据。 */
@Entity(tableName = "encrypted_accounts")
internal data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "key_version") val keyVersion: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
)