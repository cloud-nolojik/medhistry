package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.MedHistryApi
import com.medhistry.data.PatientBriefing
import kotlinx.coroutines.launch

/**
 * Doctor's QR scanner + patient briefing.
 *
 * Dev mode: text input for pasting a QR token. Production swap-in would use
 * CameraX + ML Kit barcode scanning. The scanned token is sent to /qr/scan
 * and the briefing is shown on success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorScanScreen(
    api: MedHistryApi,
    onBack: () -> Unit,
) {
    var qrInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var briefing by remember { mutableStateOf<PatientBriefing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = DoctorColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (briefing != null) "Patient Briefing" else "Scan QR",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = DoctorColors.Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DoctorColors.Background,
                    titleContentColor = DoctorColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        val b = briefing
        if (b != null) {
            PatientBriefingCard(
                briefing = b,
                onDone = { briefing = null; qrInput = ""; onBack() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF111111)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Camera viewfinder\n(CameraX + ML Kit\nin production)",
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Dev mode: paste QR token",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoctorColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qrInput,
                    onValueChange = { qrInput = it },
                    label = { Text("QR token") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DoctorColors.Danger, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isScanning = true
                            error = null
                            try {
                                briefing = api.scanQR(qrInput.trim())
                            } catch (e: Exception) {
                                error = e.message ?: "Scan failed"
                            } finally {
                                isScanning = false
                            }
                        }
                    },
                    enabled = qrInput.isNotBlank() && !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = DoctorColors.Primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text("Verify QR", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
