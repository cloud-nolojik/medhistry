package com.medhistry.patient.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Patient profile & settings. Shows name, phone, settings rows, and logout.
 */
@Composable
fun PatientProfileScreen(
    name: String,
    phone: String,
    onBack: () -> Unit,
    onManageFamily: () -> Unit,
    onAccessHistory: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\u2039", fontSize = 28.sp, color = MedHistryColors.TextPrimary, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(16.dp))
            Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MedHistryColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
            Text("+91 $phone", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
        }

        Spacer(Modifier.height(12.dp))

        SettingsRow("\uD83D\uDC64", "Personal Details", "Name, phone, date of birth", onClick = {})
        SettingsRow("\uD83D\uDC6A", "Family Members", "Manage dependents you share for", onClick = onManageFamily)
        SettingsRow("\uD83D\uDD12", "Privacy & Consent", "Manage who sees your data", onClick = onAccessHistory)
        SettingsRow("\uD83C\uDF10", "Language", "English", onClick = {})
        SettingsRow("\uD83D\uDCDE", "Help & Support", "FAQs, contact us", onClick = {})
        SettingsRow("\uD83D\uDEAA", "Log Out", null, onClick = onLogout, danger = true)
    }
}

@Composable
private fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit = {},
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) MedHistryColors.Danger else MedHistryColors.TextPrimary,
            )
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }
        if (!danger) Text("\u203A", fontSize = 18.sp, color = MedHistryColors.TextLight)
    }
}
