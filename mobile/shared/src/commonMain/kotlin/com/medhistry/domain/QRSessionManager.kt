package com.medhistry.domain

import com.medhistry.data.MedHistryApi
import com.medhistry.data.QRGenerateResponse
import com.medhistry.data.QRRefreshResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the QR code sharing session lifecycle.
 *
 * Handles: starting a session, auto-refreshing the token every 60s,
 * and ending the session. Used by the patient app's Share screen.
 */
class QRSessionManager(private val api: MedHistryApi) {

    private val _state = MutableStateFlow<QRSessionState>(QRSessionState.Idle)
    val state: StateFlow<QRSessionState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var currentSessionId: String? = null

    suspend fun startSession() {
        _state.value = QRSessionState.Loading
        try {
            val response = api.generateQR()
            currentSessionId = response.sessionId
            _state.value = QRSessionState.Active(
                qrToken = response.qrToken,
                tokenVersion = response.tokenVersion,
                expiresAt = response.expiresAt,
            )
            startAutoRefresh(response.sessionId)
        } catch (e: Exception) {
            _state.value = QRSessionState.Error(e.message ?: "Failed to start QR session")
        }
    }

    private fun startAutoRefresh(sessionId: String) {
        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(55_000) // Refresh 5s before expiry (60s token lifetime)
                try {
                    val response = api.refreshQR(sessionId)
                    _state.value = QRSessionState.Active(
                        qrToken = response.qrToken,
                        tokenVersion = response.tokenVersion,
                        expiresAt = response.expiresAt,
                    )
                } catch (e: Exception) {
                    _state.value = QRSessionState.Error("QR refresh failed: ${e.message}")
                    break
                }
            }
        }
    }

    suspend fun endSession() {
        refreshJob?.cancel()
        refreshJob = null
        currentSessionId?.let { sessionId ->
            try {
                api.endSession(sessionId)
            } catch (_: Exception) {
                // Best effort — session will expire anyway
            }
        }
        currentSessionId = null
        _state.value = QRSessionState.Idle
    }
}

sealed class QRSessionState {
    data object Idle : QRSessionState()
    data object Loading : QRSessionState()
    data class Active(
        val qrToken: String,
        val tokenVersion: Int,
        val expiresAt: String,
    ) : QRSessionState()
    data class Error(val message: String) : QRSessionState()
}
