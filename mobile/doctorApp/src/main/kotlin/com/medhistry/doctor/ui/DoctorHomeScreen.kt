package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Doctor dashboard home.
 *   - Greeting + profile avatar
 *   - Gradient "Scan Patient QR" hero card
 *   - "Today's Patients" list of recent briefings (mock)
 *   - "This Week" stats (patients viewed, avg briefing time)
 */
@Composable
fun DoctorHomeScreen(
    doctorName: String,
    onScanQR: () -> Unit,
    onEnterCode: () -> Unit,
    onProfile: () -> Unit,
    onPatientTap: (briefingId: String) -> Unit = {},
) {
    val firstName = doctorName.split(" ").firstOrNull() ?: doctorName
    val initials = doctorName.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DoctorColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Good morning,",
                    fontSize = 14.sp,
                    color = DoctorColors.TextSecondary,
                )
                Text(
                    "Dr. $firstName",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoctorColors.TextPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
                        )
                    )
                    .clickable { onProfile() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials.ifEmpty { "D" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }

        // Hero scan card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
                    )
                )
                .clickable { onScanQR() }
                .padding(24.dp),
        ) {
            Column {
                Text(
                    "SCAN PATIENT QR",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Start a briefing",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Point your camera at the patient's QR code",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("\uD83D\uDCF7  Open Scanner", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "or enter code",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onEnterCode() },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Today's Patients
        SectionHeader("Today's Patients", trailing = "See all")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No patients seen today",
                fontSize = 15.sp,
                color = DoctorColors.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Scan a patient QR to get started",
                fontSize = 13.sp,
                color = DoctorColors.TextLight,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (false) { // Placeholder — will be populated from API
            }
        }

        Spacer(Modifier.height(20.dp))

        // This Week stats
        SectionHeader("This Week")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("0", "Patients viewed", Modifier.weight(1f))
            StatCard("--", "Avg. briefing time", Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
    }
}

private data class PatientQuickRow(
    val initials: String,
    val bg: Color,
    val name: String,
    val meta: String,
    val time: String,
)

@Composable
private fun PatientRow(p: PatientQuickRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(p.bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                p.initials,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorColors.TextPrimary)
            Text(p.meta, fontSize = 12.sp, color = DoctorColors.TextSecondary)
        }
        Text(p.time, fontSize = 12.sp, color = DoctorColors.TextLight, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
        trailing?.let {
            Text(it, fontSize = 13.sp, color = DoctorColors.Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DoctorColors.Primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = DoctorColors.TextSecondary)
    }
}
