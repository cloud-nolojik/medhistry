package com.medhistry.patient.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
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
import com.medhistry.data.FamilyListResponse
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
 * The screen owns a "Sharing for" selector at the top so the patient can
 * switch between themselves and any dependent without leaving the screen.
 *
 * Both QR and 6-digit Code modes support primary + dependents — the
 * backend resolves the target patient via /qr/generate (and
 * /qr/generate-code), validating that the primary manages the dependent.
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
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var selectedPatientId by remember { mutableStateOf(patientId) }

    LaunchedEffect(Unit) {
        runCatching { api.listFamily() }.onSuccess { family = it }
    }

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
            SharingForSelector(
                family = family,
                selectedPatientId = selectedPatientId,
                onSelect = { selectedPatientId = it },
            )
            Spacer(Modifier.height(16.dp))

            ModeTabs(mode = mode, onModeChange = { mode = it })
            Spacer(Modifier.height(20.dp))

            when (mode) {
                ShareMode.QR -> QRMode(sessionManager, selectedPatientId)
                ShareMode.CODE -> CodeMode(api, selectedPatientId)
            }
        }
    }
}

@Composable
private fun SharingForSelector(
    family: FamilyListResponse?,
    selectedPatientId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val primaryFirstName: String = family?.primary?.name
        ?.split(" ")?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "Me"
    val activeName: String = when {
        selectedPatientId == null -> primaryFirstName
        else -> family?.dependents?.firstOrNull { it.id == selectedPatientId }?.name ?: "—"
    }
    val activeRelationship: String? = if (selectedPatientId == null) {
        "You"
    } else {
        family?.dependents?.firstOrNull { it.id == selectedPatientId }?.relationship
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MedHistryColors.Surface)
                .border(1.dp, MedHistryColors.PrimaryLight, RoundedCornerShape(14.dp))
                .clickable(enabled = family != null) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Health records for",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MedHistryColors.TextLight,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    activeName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MedHistryColors.TextPrimary,
                )
                activeRelationship?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MedHistryColors.TextSecondary,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MedHistryColors.Primary,
                modifier = Modifier.size(24.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            primaryFirstName,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("You", fontSize = 11.sp, color = MedHistryColors.TextSecondary)
                    }
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            family?.dependents?.forEach { dep ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(dep.name, fontWeight = FontWeight.SemiBold)
                            dep.relationship?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 11.sp, color = MedHistryColors.TextSecondary)
                            }
                        }
                    },
                    onClick = {
                        onSelect(dep.id)
                        expanded = false
                    },
                )
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
private fun QRMode(sessionManager: QRSessionManager, patientId: String?) {
    val state by sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Restart the session whenever the active "Sharing for" target changes,
    // so the QR code rotates to the correct patient/dependent.
    LaunchedEffect(patientId) {
        sessionManager.endSession()
        sessionManager.startSession(patientId)
    }
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
                        "Live \u2022 updates automatically \u2022 expires after your visit",
                        color = MedHistryColors.AccentDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            is QRSessionState.Error -> {
                Text(s.message, color = MedHistryColors.Danger, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { scope.launch { sessionManager.startSession(patientId) } }) {
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

    // Re-generate when the active patient changes.
    LaunchedEffect(patientId) {
        isLoading = true
        error = null
        code = null
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
            "Expires after 5 minutes for security \u2022 single use",
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
