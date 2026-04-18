package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Patient profile & settings. Shows name, phone, settings rows, and logout.
 *
 * [showBackArrow] should be false when used as the Profile bottom-nav tab
 * (no parent screen to go back to) and true when pushed as a sub-screen.
 */
@Composable
fun PatientProfileScreen(
    name: String,
    phone: String,
    onBack: () -> Unit,
    onManageFamily: () -> Unit,
    onAccessHistory: () -> Unit,
    onLogout: () -> Unit,
    showBackArrow: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp), // space for bottom nav
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MedHistryColors.TextPrimary,
                    modifier = Modifier.size(24.dp).clickable { onBack() },
                )
                Spacer(Modifier.width(16.dp))
            }
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

        SettingsRow(Icons.Outlined.Person, "Personal Details", "Name, phone, date of birth", onClick = {})
        SettingsRow(Icons.Outlined.People, "Family Members", "Manage the family this account covers", onClick = onManageFamily)
        // Renamed from "Privacy & Consent" — patients know "who can see my
        // records" immediately, whereas "consent" reads like a legal form.
        SettingsRow(Icons.Outlined.Lock, "Who can see my records", "Review access and sharing", onClick = onAccessHistory)
        SettingsRow(Icons.Outlined.Language, "Language", "English", onClick = {})
        SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support", "FAQs, contact us", onClick = {})
        SettingsRow(Icons.AutoMirrored.Outlined.Logout, "Log Out", null, onClick = onLogout, danger = true)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) MedHistryColors.Danger else MedHistryColors.Primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
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
        if (!danger) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MedHistryColors.TextLight,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
