package com.example.authenticator.feature.transfer.api

import com.example.authenticator.arch.api.AccessLease
import com.example.authenticator.arch.api.AccountId
import com.example.authenticator.common.basic.PasswordBytes

enum class ExportFormat { EncryptedJson, PlaintextUri }
data class ExportRequest(val requestId: String, val accountIds: Set<AccountId>, val format: ExportFormat, val riskConfirmed: Boolean)
interface ExportService { suspend fun execute(request: ExportRequest, target: OutputTarget, lease: AccessLease, password: PasswordBytes?): ExportResult }
interface OutputTarget { val reference: String; suspend fun open(): OutputSession }
interface OutputSession { suspend fun write(bytes: ByteArray); suspend fun finish(); suspend fun abort() }
sealed interface ExportResult { data object Completed : ExportResult; data object Cancelled : ExportResult; data object Locked : ExportResult; data object InvalidRequest : ExportResult; data object Failed : ExportResult }
