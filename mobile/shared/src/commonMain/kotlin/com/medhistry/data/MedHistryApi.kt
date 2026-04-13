package com.medhistry.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        throw Exception(friendlyMsg)
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
                // Already-friendly messages from ensureSuccess() — pass through
                msg.startsWith("your ") || msg.startsWith("our ") ||
                msg.startsWith("too many") || msg.startsWith("something ") ||
                msg.startsWith("you don't") || msg.startsWith("the requested")
                    -> e.message!!
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

    suspend fun generateQR(): QRGenerateResponse {
        return client.post("$baseUrl/qr/generate") {
            bearerAuth(authToken ?: throw IllegalStateException("Not authenticated"))
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

    // --- Patient Profile ---

    suspend fun getMyProfile(): PatientProfile {
        return client.get("$baseUrl/patients/me") {
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

    // --- Doctor Auth ---

    suspend fun registerDoctor(request: DoctorRegisterRequest): DoctorTokenResponse {
        val response = client.post("$baseUrl/doctors/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
        val result = response.body<DoctorTokenResponse>()
        doctorToken = result.accessToken
        return result
    }

    suspend fun loginDoctor(phone: String, password: String): DoctorTokenResponse {
        val response = client.post("$baseUrl/doctors/login") {
            contentType(ContentType.Application.Json)
            setBody(DoctorLoginRequest(phone, password))
        }.ensureSuccess()
        val result = response.body<DoctorTokenResponse>()
        doctorToken = result.accessToken
        return result
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
}
