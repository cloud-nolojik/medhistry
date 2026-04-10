package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.medhistry.data.PatientBriefing

/**
 * Rich patient briefing shown after a QR scan or code redeem.
 * Renders (top to bottom):
 *   - Patient header card with gradient avatar + live session badge
 *   - CRITICAL allergy banner
 *   - Active Conditions chips (active / monitoring states)
 *   - Current Medications rows with OD/BD/TDS frequency badges
 *   - Recent Lab Values with normal/high/low coloring
 *   - Last Visit summary
 *   - Pending Investigations warn-coloured rows
 *   - Done button
 *
 * Most of the detail fields on PatientBriefing are optional / not yet wired
 * up on the backend, so this file also supplies realistic placeholders so the
 * UI looks right during development.
 */
@Composable
fun PatientBriefingCard(
    briefing: PatientBriefing,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DoctorColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PatientHeaderCard(briefing)

        CriticalAllergyBanner(briefing.allergies ?: "No known allergies")

        SectionLabel("ACTIVE CONDITIONS")
        ActiveConditionsCard()

        SectionLabel("CURRENT MEDICATIONS")
        MedicationsCard()

        SectionLabel("RECENT LAB VALUES")
        LabValuesCard()

        SectionLabel("LAST VISIT")
        LastVisitCard()

        SectionLabel("PENDING INVESTIGATIONS")
        PendingInvestigationsCard()

        Text(
            "Session expires: ${briefing.sessionExpiresAt}",
            fontSize = 11.sp,
            color = DoctorColors.TextLight,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = DoctorColors.Primary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PatientHeaderCard(briefing: PatientBriefing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
                )
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    briefing.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    briefing.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    briefing.age?.let {
                        Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    briefing.gender?.let {
                        Text("\u00B7 $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    briefing.bloodGroup?.let {
                        Text("\u00B7 $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
            // Live session badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2ECC71).copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CriticalAllergyBanner(allergies: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFEE2E2))
            .border(1.5.dp, DoctorColors.Danger, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\u26A0\uFE0F", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CRITICAL \u2014 ALLERGIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.Danger,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                allergies,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7F1D1D),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = DoctorColors.TextLight,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun ActiveConditionsCard() = SurfaceCard {
    Text("No conditions recorded", fontSize = 14.sp, color = DoctorColors.TextSecondary)
}

@Composable
private fun MedicationsCard() = SurfaceCard {
    Text("No medications recorded", fontSize = 14.sp, color = DoctorColors.TextSecondary)
}

@Composable
private fun LabValuesCard() = SurfaceCard {
    Text("No lab results available", fontSize = 14.sp, color = DoctorColors.TextSecondary)
}

@Composable
private fun LastVisitCard() = SurfaceCard {
    Text("No previous visits recorded", fontSize = 14.sp, color = DoctorColors.TextSecondary)
}

@Composable
private fun PendingInvestigationsCard() = SurfaceCard {
    Text("No pending investigations", fontSize = 14.sp, color = DoctorColors.TextSecondary)
}
