package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Bottom-sheet-style dialog shown when a doctor taps a past patient row in
 * "Today's Patients".
 *
 * MedHistry's consent model is time-bounded: the patient's QR / share code
 * grants access only until session_expires_at. We do NOT re-open the stored
 * briefing after that window — doing so would silently convert a one-time
 * consent into unbounded access. Instead we show last-access metadata and
 * clear CTAs to start a new briefing via a fresh QR scan or share code.
 */
@Composable
fun PatientLastAccessSheet(
    patientName: String,
    accessedAtIso: String,
    method: String,
    onScanQR: () -> Unit,
    onEnterCode: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DoctorColors.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Avatar + name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DoctorColors.PrimaryLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        patientName.split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }
                            .joinToString("")
                            .ifEmpty { "?" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DoctorColors.PrimaryDark,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        patientName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoctorColors.TextPrimary,
                    )
                    Text(
                        "Last access: ${formatLastAccess(accessedAtIso)} \u00B7 ${methodLabel(method)}",
                        fontSize = 12.sp,
                        color = DoctorColors.TextSecondary,
                    )
                }
            }

            // Explanation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DoctorColors.Background)
                    .border(1.dp, DoctorColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "Session closed",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoctorColors.TextLight,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Medical details are only visible during an active share session. " +
                        "Ask the patient to share a new code or QR to view their records again.",
                    fontSize = 13.sp,
                    color = DoctorColors.TextPrimary,
                )
            }

            // Primary CTA: Enter new code
            PrimaryActionButton(
                label = "Enter new code",
                onClick = {
                    onDismiss()
                    onEnterCode()
                },
            )

            // Secondary CTA: Scan QR
            SecondaryActionButton(
                label = "Scan QR",
                onClick = {
                    onDismiss()
                    onScanQR()
                },
            )

            // Dismiss
            Text(
                "Close",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DoctorColors.TextLight,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Primary)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun SecondaryActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, DoctorColors.Primary, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DoctorColors.Primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

private val LAST_ACCESS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun formatLastAccess(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(LAST_ACCESS_FORMATTER)
}.getOrDefault(iso)

private fun methodLabel(method: String): String = when (method) {
    "qr_scan" -> "QR scan"
    "share_code" -> "Share code"
    else -> method
}
