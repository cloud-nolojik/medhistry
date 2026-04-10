package com.medhistry.patient.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.medhistry.data.MedHistryApi
import com.medhistry.data.ShareCodeGenerateResponse
import com.medhistry.domain.QRSessionManager
import com.medhistry.domain.QRSessionState
import kotlinx.coroutines.launch

enum class ShareMode { QR, CODE }

/**
 * Unified share screen with two modes: QR (the fast path) and 6-digit Code
 * (the fallback for when the camera fails or the doctor is on the phone).
 *
 * Patient picks the target (self or a dependent) on the Home screen, so
 * both modes accept an optional [patientId].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    api: MedHistryApi,
    sessionManager: QRSessionManager,
    initialMode: ShareMode,
    patientId: String?,
    onClose: () -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }

    Scaffold(
        containerColor = MedHistryColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Share with doctor", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("Close", color = MedHistryColors.Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedHistryColors.Background,
                    titleContentColor = MedHistryColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeTabs(mode = mode, onModeChange = { mode = it })
            Spacer(Modifier.height(24.dp))
            when (mode) {
                ShareMode.QR -> QRMode(sessionManager)
                ShareMode.CODE -> CodeMode(api, patientId)
            }
        }
    }
}

@Composable
private fun ModeTabs(mode: ShareMode, onModeChange: (ShareMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.PrimaryLight)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabButton("QR Code", mode == ShareMode.QR, Modifier.weight(1f)) { onModeChange(ShareMode.QR) }
        TabButton("6-digit Code", mode == ShareMode.CODE, Modifier.weight(1f)) { onModeChange(ShareMode.CODE) }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MedHistryColors.Primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else MedHistryColors.Primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

// ---------- QR mode ----------

@Composable
private fun QRMode(sessionManager: QRSessionManager) {
    val state by sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { sessionManager.startSession() }
    DisposableEffect(Unit) {
        onDispose { scope.launch { sessionManager.endSession() } }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Ask your doctor to scan this",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is QRSessionState.Loading, QRSessionState.Idle -> {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MedHistryColors.Surface)
                        .border(2.dp, MedHistryColors.PrimaryLight, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MedHistryColors.Primary)
                }
            }
            is QRSessionState.Active -> {
                Card(
                    modifier = Modifier.size(280.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = remember(s.qrToken) { generateQRBitmap(s.qrToken, 600) }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR code for doctor access",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF0FDF4))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MedHistryColors.AccentDark),
                    )
                    Text(
                        "Live \u2022 refreshes every 60s \u2022 #${s.tokenVersion}",
                        color = MedHistryColors.AccentDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            is QRSessionState.Error -> {
                Text(s.message, color = MedHistryColors.Danger, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { scope.launch { sessionManager.startSession() } }) {
                    Text("Retry")
                }
            }
        }
    }
}

// ---------- 6-digit code mode ----------

@Composable
private fun CodeMode(api: MedHistryApi, patientId: String?) {
    var code by remember { mutableStateOf<ShareCodeGenerateResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(patientId) {
        isLoading = true
        error = null
        try {
            code = api.generateShareCode(patientId)
        } catch (e: Exception) {
            error = e.message ?: "Failed to generate code"
        } finally {
            isLoading = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Read this code to your doctor",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Valid for 5 minutes \u2022 single use",
            fontSize = 12.sp,
            color = MedHistryColors.TextLight,
        )
        Spacer(Modifier.height(28.dp))

        when {
            isLoading -> CircularProgressIndicator(color = MedHistryColors.Primary)
            error != null -> {
                Text(error!!, color = MedHistryColors.Danger, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            code = api.generateShareCode(patientId)
                        } catch (e: Exception) {
                            error = e.message ?: "Failed"
                        } finally {
                            isLoading = false
                        }
                    }
                }) { Text("Retry") }
            }
            code != null -> {
                CodeDigits(code!!.shareCode)
                Spacer(Modifier.height(20.dp))
                Text(
                    "For ${code!!.patientName}",
                    fontSize = 13.sp,
                    color = MedHistryColors.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                code = api.generateShareCode(patientId)
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Generate a new code", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CodeDigits(code: String) {
    // Space after 3 digits for readability: 123 456
    val padded = code.padEnd(6, ' ')
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        padded.forEachIndexed { i, ch ->
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MedHistryColors.PrimaryLight)
                    .border(2.dp, MedHistryColors.Primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ch.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedHistryColors.Primary,
                )
            }
            if (i == 2) Spacer(Modifier.width(6.dp))
        }
    }
}

/** Generate a QR code bitmap from a string using ZXing. */
private fun generateQRBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF1A1A2E.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
