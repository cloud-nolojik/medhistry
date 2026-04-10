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
 * Doctor profile & settings.
 *   - Gradient 80dp avatar, name, specialization, hospital
 *   - Stats row (patients this week / avg time / total briefings)
 *   - Account / Preferences / Support sections with rows
 *   - Log out (danger row)
 */
@Composable
fun DoctorProfileScreen(
    doctorName: String,
    specialization: String,
    hospital: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val initials = doctorName.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "D" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DoctorColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2039",
                fontSize = 28.sp,
                color = DoctorColors.TextPrimary,
                modifier = Modifier.clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text("Dr. $doctorName", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
            Text(specialization, fontSize = 13.sp, color = DoctorColors.TextSecondary)
            Text(hospital, fontSize = 12.sp, color = DoctorColors.TextLight)
        }

        Spacer(Modifier.height(16.dp))

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatPill("18", "This week", Modifier.weight(1f))
            StatPill("24s", "Avg time", Modifier.weight(1f))
            StatPill("127", "Total", Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("ACCOUNT")
        SettingsRow("\uD83D\uDC64", "Personal Details", "Name, specialization, registration")
        SettingsRow("\uD83C\uDFE5", "Hospital & Department", hospital)
        SettingsRow("\uD83D\uDCDE", "Phone Number", "Update your mobile number")

        SectionLabel("PREFERENCES")
        SettingsRow("\uD83D\uDD14", "Notifications", "Briefings, reminders")
        SettingsRow("\uD83C\uDF10", "Language", "English")
        SettingsRow("\uD83C\uDF19", "Appearance", "Light")

        SectionLabel("SUPPORT")
        SettingsRow("\u2753", "Help Center", "FAQs, contact us")
        SettingsRow("\uD83D\uDCDC", "Privacy & Terms", null)
        SettingsRow("\u2139\uFE0F", "About MedHistry", "Version 1.0.0")

        Spacer(Modifier.height(16.dp))

        SettingsRow("\uD83D\uDEAA", "Log Out", null, danger = true, onClick = onLogout)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = DoctorColors.TextLight,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DoctorColors.Primary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = DoctorColors.TextSecondary)
    }
}

@Composable
private fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) DoctorColors.Danger else DoctorColors.TextPrimary,
            )
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = DoctorColors.TextSecondary)
            }
        }
        if (!danger) Text("\u203A", fontSize = 18.sp, color = DoctorColors.TextLight)
    }
}
