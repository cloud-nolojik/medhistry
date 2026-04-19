package com.medhistry.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thrown by [MedHistryApi.ensureSuccess] when the backend returns a non-2xx
 * response with a human-readable `detail` field. The [message] is the exact
 * backend message and is safe to show to the user verbatim.
 */
class ApiException(message: String, val httpStatus: Int) : Exception(message)

/**
 * MedHistry API client — shared across Android and iOS.
 * Handles patient registration, login, QR generation/refresh, and doctor scan.
 */
class MedHistryApi(private val baseUrl: String = "https://app.medhistry.com/api/v1") {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // Full request/response logging — method, URL, headers, body in & out,
        // plus response status. Shows up in Android logcat under tag
        // `MedHistryApi` (filter: `adb logcat *:S MedHistryApi:V`).
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    // println goes to System.out → logcat tag "System.out".
                    // Prefix every line so it's grep-able.
                    message.lineSequence().forEach { line ->
                        println("MedHistryApi >> $line")
                    }
                }
            }
        }
    }

    private var authToken: String? = null
    private var doctorToken: String? = null

    /**
     * Check HTTP response status. If not successful, try to extract a
     * human-readable "detail" message from the JSON error body and throw it.
     * Raw HTTP codes are never shown to the user — we map them to friendly messages.
     */
    private suspend fun HttpResponse.ensureSuccess(): HttpResponse {
        if (status.isSuccess()) return this
        val serverDetail = try {
            val errorBody = body<JsonObject>()
            errorBody["detail"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        val friendlyMsg = serverDetail ?: friendlyError(status.value)
        throw ApiException(friendlyMsg, status.value)
    }

    private fun friendlyError(code: Int): String = when (code) {
        401 -> "Your session has expired. Please log in again."
        403 -> "You don't have permission to do this."
        404 -> "The requested data was not found."
        408 -> "The request timed out. Please try again."
        422 -> "Something was wrong with the data sent. Please try again."
        429 -> "Too many requests. Please wait a moment and try again."
        in 500..599 -> "Our servers are temporarily unavailable. Please try again in a moment."
        else -> "Something went wrong. Please try again."
    }

    companion object {
        /**
         * Convert any exception into a patient-friendly message.
         * Call this from UI code: `catch (e: Exception) { error = MedHistryApi.friendlyMessage(e) }`
         */
        fun friendlyMessage(e: Throwable): String {
            // Backend-sourced errors: show the backend's `detail` verbatim —
            // the server is the source of truth for user-facing API errors.
            if (e is ApiException) return e.message ?: "Something went wrong. Please try again."

            val msg = e.message?.lowercase() ?: ""
            return when {
                // Network / connection errors
                "unable to resolve host" in msg || "unknownhost" in msg
                    -> "Can't reach our servers. Please check your internet connection."
                "connect" in msg && ("refused" in msg || "failed" in msg || "timed out" in msg)
                    -> "Can't connect to our servers. Please check your internet and try again."
                "timeout" in msg || "timed out" in msg
                    -> "The request timed out. Please try again."
                "no internet" in msg || "network" in msg
                    -> "No internet connection. Please check your network and try again."
                "ssl" in msg || "certificate" in msg
                    -> "Secure connection failed. Please try again."
                // Fallback
                else -> "Something went wrong. Please try again."
            }
        }
    }

    fun setToken(token: String) {
        authToken = token
    }

    fun setDoctorToken(token: String) {
        doctorToken = token
    }

    // --- Patient Auth (OTP + PIN flow) ---

    suspend fun sendOTP(phone: String): SendOTPResponse {
        return client.post("$baseUrl/patients/send-otp") {
            contentType(ContentType.Application.Json)
            setBody(SendOTPRequest(phone))
        }.body()
    }

    suspend fun verifyOTP(phone: String, otp: String): VerifyOTPResponse {
        return client.post("$baseUrl/patients/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(VerifyOTPRequest(phone, otp))
        }.body()
    }

    suspend fun completeRegistration(request: CompleteRegistrationRequest): TokenResponse {
        val response = client.post("$baseUrl/patients/complete-registration") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
        val result = response.body<TokenResponse>()
        authToken = result.accessToken
        return result
    }

    suspend fun pinLogin(phone: String, pin: String): TokenResponse {
        val response = client.post("$baseUrl/patients/pin-login") {
            contentType(ContentType.Application.Json)
            setBody(PinLoginRequest(phone, pin))
        }.ensureSuccess()
        val result = response.body<TokenResponse>()
        authToken = result.accessToken
        return result
    }

    // --- Patient Auth (legacy — kept for backward compat) ---

    suspend fun register(request: RegisterRequest): TokenResponse {
        val response = client.post("$baseUrl/patients/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
        val result = response.body<TokenResponse>()
        authToken = result.accessToken
        return result
    }

    suspend fun login(phone: String, password: String): TokenResponse {
        val response = client.post("$baseUrl/patients/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(phone, password))
        }.ensureSuccess()
        val result = response.body<TokenResponse>()
        authToken = result.accessToken
        return result
    }

    // --- QR Session ---

    suspend fun generateQR(patientId: String? = null): QRGenerateResponse {
        return client.post("$baseUrl/qr/generate") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(QRGenerateRequest(patientId))
        }.body()
    }

    suspend fun refreshQR(sessionId: String): QRRefreshResponse {
        return client.post("$baseUrl/qr/refresh/$sessionId") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.body()
    }

    suspend fun endSession(sessionId: String) {
        client.post("$baseUrl/qr/end/$sessionId") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }
    }

    suspend fun generateShareCode(patientId: String? = null): ShareCodeGenerateResponse {
        return client.post("$baseUrl/qr/generate-code") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(ShareCodeGenerateRequest(patientId))
        }.body()
    }

    // --- My Profile ---

    suspend fun getMyProfile(): PatientProfile {
        return client.get("$baseUrl/patients/me") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    suspend fun updateMyProfile(request: ProfileUpdateRequest): PatientProfile {
        return client.patch("$baseUrl/patients/me") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(request)
        }.ensureSuccess().body()
    }

    // --- Family / Dependents ---

    suspend fun listFamily(): FamilyListResponse {
        return client.get("$baseUrl/patients/family") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.body()
    }

    suspend fun addDependent(request: DependentCreateRequest): DependentOut {
        return client.post("$baseUrl/patients/family") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(request)
        }.body()
    }

    suspend fun updateDependent(dependentId: String, request: DependentCreateRequest): DependentOut {
        return client.put("$baseUrl/patients/family/$dependentId") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(request)
        }.ensureSuccess().body()
    }

    suspend fun removeDependent(dependentId: String) {
        client.delete("$baseUrl/patients/family/$dependentId") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }
    }

    // --- Documents (Azure SAS upload flow) ---

    suspend fun requestUploadUrl(request: UploadUrlRequest): UploadUrlResponse {
        return client.post("$baseUrl/documents/upload-url") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(request)
        }.body()
    }

    suspend fun confirmUpload(documentId: String): DocumentOut {
        return client.post("$baseUrl/documents/confirm") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(ConfirmUploadRequest(documentId))
        }.body()
    }

    suspend fun listDocuments(patientId: String? = null, includeFamily: Boolean = false): DocumentListOut {
        return client.get("$baseUrl/documents/") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            patientId?.let { parameter("patient_id", it) }
            if (includeFamily) parameter("include_family", "true")
        }.body()
    }

    suspend fun getDocument(documentId: String): DocumentOut {
        return client.get("$baseUrl/documents/$documentId") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.body()
    }

    suspend fun getDocumentFileUrl(documentId: String): FileUrlResponse {
        return client.get("$baseUrl/documents/$documentId/file") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    suspend fun deleteDocument(documentId: String) {
        client.delete("$baseUrl/documents/$documentId") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess()
    }

    suspend fun getHealthSummary(patientId: String? = null): PatientHealthSummary {
        return client.get("$baseUrl/documents/summary/health") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            patientId?.let { parameter("patient_id", it) }
        }.body()
    }

    // --- Access Log ---

    suspend fun getAccessLog(patientId: String): List<AccessLogEntry> {
        return client.get("$baseUrl/patients/$patientId/access-log") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.body()
    }

    /**
     * Upload file bytes directly to Azure Blob Storage using SAS URL.
     * Returns the HTTP status code.
     */
    suspend fun uploadToAzure(sasUrl: String, fileBytes: ByteArray, contentType: String): Int {
        val response = client.put(sasUrl) {
            header("x-ms-blob-type", "BlockBlob")
            contentType(ContentType.parse(contentType))
            setBody(fileBytes)
        }
        return response.status.value
    }

    // --- Doctor Invite Verification ---

    /**
     * Legacy GET-by-code verification. Does NOT enforce phone match — prefer
     * [verifyDoctorInvite] once the doctor has a temp_token from OTP verify.
     */
    suspend fun verifyInviteCode(code: String): InviteVerifyResponse {
        return client.get("$baseUrl/doctors/verify-invite/$code")
            .ensureSuccess()
            .body()
    }

    /**
     * Verify the invite AND that it was issued to the same phone the doctor
     * just OTP-verified. Backend returns 400 with a user-facing message when
     * the numbers don't match — bubble the message up to the UI verbatim.
     */
    suspend fun verifyDoctorInvite(
        inviteCode: String,
        tempToken: String,
    ): InviteVerifyResponse {
        return client.post("$baseUrl/doctors/verify-invite") {
            contentType(ContentType.Application.Json)
            setBody(DoctorVerifyInviteRequest(inviteCode, tempToken))
        }.ensureSuccess().body()
    }

    // --- Doctor Auth (OTP-based) ---

    suspend fun sendDoctorOTP(phone: String): DoctorSendOTPResponse {
        return client.post("$baseUrl/doctors/send-otp") {
            contentType(ContentType.Application.Json)
            setBody(DoctorSendOTPRequest(phone))
        }.ensureSuccess().body()
    }

    /**
     * Verify doctor OTP. If [DoctorVerifyOTPResponse.isNewUser] is false, the
     * doctor is logged in — the access token is stored on this client so
     * subsequent calls are authenticated. If [isNewUser] is true, the caller
     * must collect an invite code and call [completeDoctorRegistration] with
     * the [tempToken].
     */
    suspend fun verifyDoctorOTP(phone: String, otp: String): DoctorVerifyOTPResponse {
        val response = client.post("$baseUrl/doctors/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(DoctorVerifyOTPRequest(phone, otp))
        }.ensureSuccess()
        val result = response.body<DoctorVerifyOTPResponse>()
        result.accessToken?.let { doctorToken = it }
        return result
    }

    suspend fun completeDoctorRegistration(
        request: DoctorCompleteRegistrationRequest,
    ): DoctorTokenResponse {
        val response = client.post("$baseUrl/doctors/complete-registration") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
        val result = response.body<DoctorTokenResponse>()
        doctorToken = result.accessToken
        return result
    }

    suspend fun getDoctorProfile(): DoctorProfile {
        return client.get("$baseUrl/doctors/me") {
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
        }.ensureSuccess().body()
    }

    /**
     * Set (or replace) the doctor's 6-digit PIN. Requires a valid doctor
     * bearer token — call this right after first OTP login, or after a
     * Forgot-PIN re-OTP flow.
     */
    suspend fun setDoctorPin(pin: String): DoctorProfile {
        return client.post("$baseUrl/doctors/set-pin") {
            contentType(ContentType.Application.Json)
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
            setBody(DoctorSetPinRequest(pin))
        }.ensureSuccess().body()
    }

    /**
     * Fast-path sign in with phone + 6-digit PIN on app open. Backend returns
     * a fresh access token; store it on this client just like verify-otp.
     */
    suspend fun doctorPinLogin(phone: String, pin: String): DoctorTokenResponse {
        val response = client.post("$baseUrl/doctors/pin-login") {
            contentType(ContentType.Application.Json)
            setBody(DoctorPinLoginRequest(phone, pin))
        }.ensureSuccess()
        val result = response.body<DoctorTokenResponse>()
        doctorToken = result.accessToken
        return result
    }

    suspend fun getDoctorDashboard(): DoctorDashboard {
        return client.get("$baseUrl/doctors/me/dashboard") {
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
        }.ensureSuccess().body()
    }

    /**
     * Paginated + filterable list of the doctor's briefings (access-log rows).
     * @param days     null = all time, otherwise last N days
     * @param method   "qr_scan", "share_code", or null for both
     * @param search   substring on patient name (case-insensitive)
     */
    suspend fun listDoctorBriefings(
        days: Int? = null,
        method: String? = null,
        search: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): DoctorBriefingsList {
        return client.get("$baseUrl/doctors/me/briefings") {
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
            days?.let { parameter("days", it) }
            method?.let { parameter("method", it) }
            search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }.ensureSuccess().body()
    }

    // --- Doctor Scan (requires doctor auth) ---

    suspend fun scanQR(qrToken: String): PatientBriefing {
        return client.post("$baseUrl/qr/scan") {
            contentType(ContentType.Application.Json)
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
            setBody(QRScanRequest(qrToken))
        }.body()
    }

    suspend fun redeemShareCode(shareCode: String): PatientBriefing {
        return client.post("$baseUrl/qr/redeem-code") {
            contentType(ContentType.Application.Json)
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
            setBody(ShareCodeRedeemRequest(shareCode))
        }.body()
    }

    /** Doctor-side: fetch a short-lived read SAS URL for an original document.
     *  The backend validates there's a live session for the patient AND the
     *  doctor has a recent access log for them — so this only works while the
     *  briefing is still "open". */
    suspend fun doctorGetDocumentFileUrl(documentId: String): FileUrlResponse {
        return client.get("$baseUrl/qr/documents/$documentId/file") {
            bearerAuth(doctorToken ?: throw IllegalStateException("Doctor not authenticated"))
        }.ensureSuccess().body()
    }

    // --- Upcoming Events ---

    suspend fun listUpcomingEvents(
        patientId: String? = null,
        includeCompleted: Boolean = false,
    ): UpcomingEventListOut {
        return client.get("$baseUrl/upcoming-events") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            patientId?.let { parameter("patient_id", it) }
            if (includeCompleted) parameter("include_completed", "true")
        }.ensureSuccess().body()
    }

    suspend fun completeUpcomingEvent(eventId: String): UpcomingEvent {
        return client.post("$baseUrl/upcoming-events/$eventId/complete") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    suspend fun dismissUpcomingEvent(eventId: String): UpcomingEvent {
        return client.post("$baseUrl/upcoming-events/$eventId/dismiss") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    /** Dismiss ONLY the "Looks done?" completion suggestion — the event stays
     *  pending. Used when the user taps the ✕ on the suggestion banner. */
    suspend fun dismissUpcomingEventSuggestion(eventId: String): UpcomingEvent {
        return client.post("$baseUrl/upcoming-events/$eventId/dismiss-suggestion") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    /** Returns the raw ICS body the OS calendar app can open via intent. */
    suspend fun getUpcomingEventIcs(eventId: String): String {
        return client.get("$baseUrl/upcoming-events/$eventId/calendar.ics") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().bodyAsText()
    }

    // --- Person-scoped Chat (all documents for a family member) ---

    /**
     * Send a message to the person-scoped AI chat.
     * The backend endpoint at /patients/chat (or /patients/{id}/chat) has
     * access to all of the person's documents, conditions, medicines, and labs.
     *
     * Session memory is managed server-side (last ~10 turns) for the duration
     * of the auth token. Wiped on re-login for privacy.
     *
     * Returns the assistant's plain-language reply as a string.
     */
    suspend fun sendPersonChat(patientId: String? = null, message: String): String {
        val url = if (patientId != null) {
            "$baseUrl/patients/$patientId/chat"
        } else {
            "$baseUrl/patients/chat"
        }
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(ChatSendRequest(message))
        }.ensureSuccess().body<ChatSendResponse>()
        return response.assistantMessage.content
    }

    /** Load persistent chat history for a patient (or dependent). */
    suspend fun getPersonChatHistory(patientId: String? = null): List<ChatMessage> {
        val url = if (patientId != null) {
            "$baseUrl/patients/$patientId/chat"
        } else {
            "$baseUrl/patients/chat"
        }
        return client.get(url) {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    // --- Per-document Chat ---

    suspend fun getChatStarters(documentId: String): ChatStartersOut {
        return client.get("$baseUrl/documents/$documentId/chat/starters") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    suspend fun getChatHistory(documentId: String): ChatMessageListOut {
        return client.get("$baseUrl/documents/$documentId/chat") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess().body()
    }

    suspend fun sendChatMessage(documentId: String, message: String): ChatSendResponse {
        return client.post("$baseUrl/documents/$documentId/chat") {
            contentType(ContentType.Application.Json)
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
            setBody(ChatSendRequest(message))
        }.ensureSuccess().body()
    }

    suspend fun resetChat(documentId: String) {
        client.delete("$baseUrl/documents/$documentId/chat") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
        }.ensureSuccess()
    }
}
