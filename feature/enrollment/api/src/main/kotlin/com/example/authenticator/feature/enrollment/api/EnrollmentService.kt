package com.example.authenticator.feature.enrollment.api

data class ManualEnrollmentRequest(val issuer: String, val accountName: String, val secret: String, val algorithm: String = "SHA1", val digits: Int = 6, val periodSeconds: Int = 30) {
    init {
        require(digits == 6 || digits == 8) { "TOTP digits must be 6 or 8" }
        require(periodSeconds in 15..120) { "TOTP period must be between 15 and 120 seconds" }
    }
    override fun toString(): String = "ManualEnrollmentRequest([REDACTED])"
}
data class UriEnrollmentRequest(val uri: String) {
    override fun toString(): String = "UriEnrollmentRequest([REDACTED])"
}
@JvmInline value class EnrollmentDraftId(val value: String) {
    override fun toString(): String = "EnrollmentDraftId([REDACTED])"
}
data class Preparation(val draftId: EnrollmentDraftId, val revision: Long, val accountName: String, val issuer: String, val algorithm: String, val digits: Int, val periodSeconds: Int) {
    init {
        require(digits == 6 || digits == 8) { "TOTP digits must be 6 or 8" }
        require(periodSeconds in 15..120) { "TOTP period must be between 15 and 120 seconds" }
    }
}
data class ConfirmEnrollment(val draftId: EnrollmentDraftId, val revision: Long, val weakSecretConfirmed: Boolean, val duplicateChallengeId: String?) {
    override fun toString(): String = "ConfirmEnrollment([REDACTED])"
}
interface EnrollmentService { suspend fun prepareManual(request: ManualEnrollmentRequest): PreparationResult; suspend fun prepareQr(request: UriEnrollmentRequest): PreparationResult; suspend fun confirm(request: ConfirmEnrollment): EnrollmentResult; fun discard(draftId: EnrollmentDraftId) }
sealed interface PreparationResult { data class Ready(val preparation: Preparation) : PreparationResult; data object Invalid : PreparationResult; data object Locked : PreparationResult }
sealed interface EnrollmentResult { data object Saved : EnrollmentResult; data object Invalid : EnrollmentResult; data object DuplicateConfirmationRequired : EnrollmentResult; data object DraftExpired : EnrollmentResult; data object Locked : EnrollmentResult; data object Failed : EnrollmentResult }
