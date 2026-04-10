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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown right after a doctor hits "Done" on a briefing.
 * Confirms the session is closed and offers two next actions.
 */
@Composable
fun DoctorSessionEndedScreen(
    patientName: String,
    onBackToDashboard: () -> Unit,
    onScanNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DoctorColors.Background)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2713", fontSize = 68.sp, color = DoctorColors.Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Session Closed",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your view of $patientName's records has been logged and closed. Access will need fresh consent next time.",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(36.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DoctorColors.Primary)
                    .clickable { onScanNext() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Scan Next Patient", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DoctorColors.Surface)
                    .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
                    .clickable { onBackToDashboard() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Back to Dashboard", color = DoctorColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
