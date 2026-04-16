package com.medhistry.doctor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.medhistry.data.MedHistryApi
import com.medhistry.data.PatientBriefing
import kotlinx.coroutines.launch

/**
 * Doctor's QR scanner + patient briefing.
 *
 * Uses CameraX + ML Kit to scan QR codes live from the back camera. On first
 * decode the token is submitted to /qr/scan and the briefing is shown. A
 * collapsible dev-mode paste field remains as a fallback for QA builds where
 * no camera is available (emulator without webcam, etc.).
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
    var devModeOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Camera permission state. Initial value reflects whether we already
    // have the permission so that returning users skip the rationale UI.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            error = "Camera permission is required to scan QR codes"
        }
    }

    // Submit helper used by both live camera detection and dev-paste button.
    fun submit(token: String) {
        if (token.isBlank() || isScanning) return
        scope.launch {
            isScanning = true
            error = null
            try {
                briefing = api.scanQR(token.trim())
            } catch (e: Exception) {
                error = e.message ?: "Scan failed"
            } finally {
                isScanning = false
            }
        }
    }

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
                onViewDocument = { docId ->
                    scope.launch {
                        try {
                            val resp = api.doctorGetDocumentFileUrl(docId)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resp.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Could not open document",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Viewfinder frame — either the live camera preview, or a
                // permission-request placeholder. Same size in both states
                // so the layout doesn't jump.
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0B0B0B))
                        .border(2.dp, DoctorColors.Primary, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasCameraPermission) {
                        // Live scanner — auto-submits on first QR decode.
                        QrCameraScanner(
                            onDetected = { raw ->
                                // Guard against double-submit while the
                                // existing request is in flight.
                                if (!isScanning && briefing == null) {
                                    submit(raw)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Aim reticle overlay — visual cue for where to
                        // hold the QR. 60% of viewfinder edge.
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .border(
                                    2.dp,
                                    Color.White.copy(alpha = 0.85f),
                                    RoundedCornerShape(14.dp),
                                ),
                        )
                        if (isScanning) {
                            // Tinted busy overlay while /qr/scan is pending.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                "Camera access needed",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Grant camera permission to scan the patient's QR code.",
                                color = Color.White.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DoctorColors.Primary,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    "Grant permission",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    if (hasCameraPermission) {
                        "Align the patient's QR code inside the frame"
                    } else {
                        "Scanner needs camera access to read the patient's QR"
                    },
                    color = DoctorColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = DoctorColors.Danger,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.weight(1f))

                // Dev-mode paste fallback. Collapsed by default so the
                // production UX is camera-first; expanded on tap for
                // emulator testing or when the camera is unavailable.
                TextButton(onClick = { devModeOpen = !devModeOpen }) {
                    Text(
                        if (devModeOpen) "Hide paste token" else "Paste token instead",
                        color = DoctorColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (devModeOpen) {
                    OutlinedTextField(
                        value = qrInput,
                        onValueChange = { qrInput = it },
                        label = { Text("QR token") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { submit(qrInput) },
                        enabled = qrInput.isNotBlank() && !isScanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DoctorColors.Primary,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                "Submit",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
