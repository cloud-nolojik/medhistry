package com.medhistry.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Doctor Auth Models (OTP-based) ---

@Serializable
data class InviteVerifyResponse(
    val valid: Boolean,
    @SerialName("hospital_name") val hospitalName: String,
    @SerialName("doctor_name") val doctorName: String? = null,
    val specialisation: String? = null,
    @SerialName("doctor_phone") val doctorPhone: String? = null,
)

@Serializable
data class DoctorVerifyInviteRequest(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("temp_token") val tempToken: String,
)

@Serializable
data class DoctorSendOTPRequest(val phone: String)

@Serializable
data class DoctorSendOTPResponse(
    val message: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
    val otp: String? = null, // DEV ONLY — autofill
)

@Serializable
data class DoctorVerifyOTPRequest(val phone: String, val otp: String)

@Serializable
data class DoctorVerifyOTPResponse(
    val verified: Boolean,
    @SerialName("is_new_user") val isNewUser: Boolean,
    @SerialName("access_token") val accessToken: String? = null,
    val doctor: DoctorProfile? = null,
    @SerialName("temp_token") val tempToken: String? = null,
)

@Serializable
data class DoctorCompleteRegistrationRequest(
    @SerialName("temp_token") val tempToken: String,
    @SerialName("invite_code") val inviteCode: String,
    val name: String? = null,
    val specialisation: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    val email: String? = null,
)

@Serializable
data class DoctorTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    val doctor: DoctorProfile,
)

@Serializable
data class DoctorProfile(
    val id: String,
    @SerialName("hospital_id") val hospitalId: String,
    @SerialName("hospital_name") val hospitalName: String? = null,
    val phone: String,
    val name: String,
    val specialisation: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    val email: String? = null,
    @SerialName("has_pin") val hasPin: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DoctorSetPinRequest(val pin: String)

@Serializable
data class DoctorPinLoginRequest(val phone: String, val pin: String)

@Serializable
data class DoctorDashboardBriefing(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("patient_name") val patientName: String,
    val method: String,
    @SerialName("accessed_at") val accessedAt: String,
)

@Serializable
data class DoctorDashboard(
    @SerialName("today_count") val todayCount: Int,
    @SerialName("week_count") val weekCount: Int,
    @SerialName("all_time_count") val allTimeCount: Int = 0,
    @SerialName("avg_briefing_seconds") val avgBriefingSeconds: Int? = null,
    @SerialName("recent_briefings") val recentBriefings: List<DoctorDashboardBriefing> = emptyList(),
)

@Serializable
data class DoctorBriefingsList(
    val briefings: List<DoctorDashboardBriefing> = emptyList(),
    val total: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

// --- Patient OTP + PIN Auth Models ---

@Serializable
data class SendOTPRequest(val phone: String)

@Serializable
data class SendOTPResponse(
    val message: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
    val otp: String? = null, // DEV ONLY — autofill in debug builds
)

@Serializable
data class VerifyOTPRequest(val phone: String, val otp: String)

@Serializable
data class VerifyOTPResponse(
    val verified: Boolean,
    @SerialName("is_new_user") val isNewUser: Boolean,
    @SerialName("temp_token") val tempToken: String,
)

@Serializable
data class CompleteRegistrationRequest(
    @SerialName("temp_token") val tempToken: String,
    val name: String,
    val pin: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
)

@Serializable
data class PinLoginRequest(val phone: String, val pin: String)

// --- Patient Legacy Models (kept for backward compat) ---

@Serializable
data class RegisterRequest(
    val phone: String,
    val name: String,
    val password: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
)

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
)

@Serializable
data class QRScanRequest(
    @SerialName("qr_token") val qrToken: String,
)

// --- Response Models ---

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    val patient: PatientProfile,
)

@Serializable
data class PatientProfile(
    val id: String,
    val phone: String? = null,
    val name: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
    @SerialName("medical_summary") val medicalSummary: String? = null,
    @SerialName("is_phone_verified") val isPhoneVerified: Boolean = false,
    @SerialName("has_pin") val hasPin: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class QRGenerateRequest(
    @SerialName("patient_id") val patientId: String? = null,
)

@Serializable
data class QRGenerateResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("qr_token") val qrToken: String,
    @SerialName("token_version") val tokenVersion: Int,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("patient_id") val patientId: String = "",
    @SerialName("patient_name") val patientName: String = "",
)

@Serializable
data class QRRefreshResponse(
    @SerialName("qr_token") val qrToken: String,
    @SerialName("token_version") val tokenVersion: Int,
    @SerialName("expires_at") val expiresAt: String,
)

// --- Share Code (6-digit fallback) ---

@Serializable
data class ShareCodeGenerateRequest(
    @SerialName("patient_id") val patientId: String? = null,
)

@Serializable
data class ShareCodeGenerateResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("share_code") val shareCode: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("patient_name") val patientName: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class ShareCodeRedeemRequest(
    @SerialName("share_code") val shareCode: String,
)

// --- Family / Dependents ---

@Serializable
data class DependentCreateRequest(
    val name: String,
    val relationship: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
    val phone: String? = null,
)

@Serializable
data class DependentOut(
    val id: String,
    val name: String,
    val phone: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
    @SerialName("medical_summary") val medicalSummary: String? = null,
    @SerialName("managed_by") val managedBy: String? = null,
    val relationship: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FamilyListResponse(
    val primary: PatientProfile,
    val dependents: List<DependentOut> = emptyList(),
)

@Serializable
data class PatientBriefing(
    @SerialName("patient_id") val patientId: String,
    val name: String,
    val age: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
    @SerialName("medical_summary") val medicalSummary: String? = null,
    val medications: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    val diagnoses: List<String> = emptyList(),
    @SerialName("critical_labs") val criticalLabs: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    @SerialName("document_notes") val documentNotes: List<DocumentNote> = emptyList(),
    @SerialName("total_documents") val totalDocuments: Int = 0,
    @SerialName("session_expires_at") val sessionExpiresAt: String,
)

/** Per-document distilled clinical content (one row per uploaded document).
 *  Surfaces the doctor-targeted `clinical_summary` Gemini extracts for each
 *  document — previously dropped on the floor by the briefing builder. */
@Serializable
data class DocumentNote(
    @SerialName("document_id") val documentId: String,
    @SerialName("doc_type") val docType: String? = null,
    @SerialName("document_date") val documentDate: String? = null,
    @SerialName("hospital_name") val hospitalName: String? = null,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("doctor_specialisation") val doctorSpecialisation: String? = null,
    @SerialName("clinical_summary") val clinicalSummary: String? = null,
    @SerialName("patient_summary") val patientSummary: String? = null,
    @SerialName("overall_status") val overallStatus: String? = null,
    @SerialName("overall_status_message") val overallStatusMessage: String? = null,
    @SerialName("follow_up") val followUp: String? = null,
    val symptoms: List<String> = emptyList(),
    val vitals: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
)

// --- Document Upload (Azure SAS flow) ---

@Serializable
data class UploadUrlRequest(
    val filename: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    @SerialName("hospital_id") val hospitalId: String? = null,
    @SerialName("doctor_id") val doctorId: String? = null,
    @SerialName("patient_id") val patientId: String? = null,
)

@Serializable
data class UploadUrlResponse(
    @SerialName("document_id") val documentId: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("blob_path") val blobPath: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
)

@Serializable
data class ConfirmUploadRequest(
    @SerialName("document_id") val documentId: String,
)

@Serializable
data class DocumentOut(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    val filename: String,
    @SerialName("file_type") val fileType: String,
    @SerialName("doc_type") val docType: String? = null,
    @SerialName("ai_summary") val aiSummary: String? = null,
    @SerialName("extracted_data") val extractedData: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("processing_status") val processingStatus: String,
    @SerialName("document_date") val documentDate: String? = null,
    @SerialName("hospital_name") val hospitalName: String? = null,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DocumentListOut(
    val documents: List<DocumentOut>,
    val total: Int,
)

@Serializable
data class FileUrlResponse(
    val url: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
)

// --- Health Summary ---

@Serializable
data class PatientHealthSummary(
    @SerialName("patient_id") val patientId: String,
    @SerialName("total_documents") val totalDocuments: Int,
    val medications: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    val diagnoses: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val vitals: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    @SerialName("lab_results") val labResults: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    @SerialName("overall_summary") val overallSummary: String? = null,
    @SerialName("patient_summary") val patientSummary: String? = null,
    @SerialName("overall_status") val overallStatus: String? = null,
    @SerialName("overall_status_message") val overallStatusMessage: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
)

// --- Access Log ---

@Serializable
data class AccessLogEntry(
    val id: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("hospital_id") val hospitalId: String? = null,
    @SerialName("hospital_name") val hospitalName: String? = null,
    val method: String,
    @SerialName("accessed_at") val accessedAt: String,
)
