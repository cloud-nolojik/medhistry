package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DoctorDashboard
import com.medhistry.data.DoctorProfile
import com.medhistry.data.MedHistryApi

/**
 * Doctor profile & settings — essentials only.
 *
 *   Header   avatar · name · specialisation · hospital
 *   Stats    This week · Avg time · Total   (live from /doctors/me/dashboard)
 *   Account  Personal Details, Hospital & Department, Phone Number
 *            (Phone Number → log out + re-OTP via [onLogout])
 *   About    Privacy & Terms, About MedHistry
 *   [danger] Log Out
 */
@Composable
fun DoctorProfileScreen(
    doctorName: String,
    specialization: String,
    hospital: String,
    api: MedHistryApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var profile by remember { mutableStateOf<DoctorProfile?>(null) }
    var dashboard by remember { mutableStateOf<DoctorDashboard?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            profile = api.getDoctorProfile()
        } catch (e: Exception) {
            loadError = MedHistryApi.friendlyMessage(e)
        }
        try {
            dashboard = api.getDoctorDashboard()
        } catch (_: Exception) {
            // dashboard is best-effort; leave null and the pills show "—"
        }
    }

    // Dialog state
    var showPersonalDetails by remember { mutableStateOf(false) }
    var showHospital by remember { mutableStateOf(false) }
    var showPhoneChange by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

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
        // Top bar
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

        // Identity block
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
                            listOf(DoctorColors.NavyDark, DoctorColors.NavyLight),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text("Dr. $doctorName", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
            if (specialization.isNotEmpty()) {
                Text(specialization, fontSize = 13.sp, color = DoctorColors.TextSecondary)
            }
            if (hospital.isNotEmpty()) {
                Text(hospital, fontSize = 12.sp, color = DoctorColors.TextLight)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats — wired to /doctors/me/dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatPill(
                value = dashboard?.weekCount?.toString() ?: "\u2014",
                label = "This week",
                loading = dashboard == null && loadError == null,
                modifier = Modifier.weight(1f),
            )
            StatPill(
                value = dashboard?.avgBriefingSeconds?.let { "${it}s" } ?: "\u2014",
                label = "Avg time",
                loading = dashboard == null && loadError == null,
                modifier = Modifier.weight(1f),
            )
            StatPill(
                value = dashboard?.allTimeCount?.toString() ?: "\u2014",
                label = "Total",
                loading = dashboard == null && loadError == null,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // ACCOUNT
        SectionLabel("ACCOUNT")
        SettingsRow(
            icon = "\uD83D\uDC64",
            title = "Personal Details",
            subtitle = "Name, specialisation, registration",
            onClick = { showPersonalDetails = true },
        )
        SettingsRow(
            icon = "\uD83C\uDFE5",
            title = "Hospital & Department",
            subtitle = hospital.ifEmpty { "—" },
            onClick = { showHospital = true },
        )
        SettingsRow(
            icon = "\uD83D\uDCDE",
            title = "Phone Number",
            subtitle = profile?.phone?.let { prettyPhoneForDisplay(it) } ?: "—",
            onClick = { showPhoneChange = true },
        )

        // ABOUT
        SectionLabel("ABOUT")
        SettingsRow(
            icon = "\uD83D\uDCDC",
            title = "Privacy & Terms",
            subtitle = null,
            onClick = { showPrivacy = true },
        )
        SettingsRow(
            icon = "\u2139\uFE0F",
            title = "About MedHistry",
            subtitle = "Version 1.0.0",
            onClick = { showAbout = true },
        )

        Spacer(Modifier.height(16.dp))

        SettingsRow(
            icon = "\uD83D\uDEAA",
            title = "Log Out",
            subtitle = null,
            danger = true,
            onClick = { showLogoutConfirm = true },
        )
    }

    // --- Dialogs ---

    if (showPersonalDetails) {
        InfoDialog(
            title = "Personal Details",
            onDismiss = { showPersonalDetails = false },
        ) {
            DetailRow("Name", profile?.name ?: doctorName)
            DetailRow("Specialisation", profile?.specialisation ?: specialization.ifEmpty { "—" })
            DetailRow("Registration", profile?.licenseNumber ?: "—")
            DetailRow("Email", profile?.email ?: "—")
            DetailRow("Phone", profile?.phone?.let { prettyPhoneForDisplay(it) } ?: "—")
            Spacer(Modifier.height(8.dp))
            Text(
                "Contact your hospital admin to update these details.",
                fontSize = 11.sp,
                color = DoctorColors.TextLight,
            )
        }
    }

    if (showHospital) {
        InfoDialog(
            title = "Hospital & Department",
            onDismiss = { showHospital = false },
        ) {
            DetailRow("Hospital", hospital.ifEmpty { profile?.hospitalName ?: "—" })
            DetailRow("Department", profile?.specialisation ?: specialization.ifEmpty { "—" })
            Spacer(Modifier.height(8.dp))
            Text(
                "Your hospital assignment is set when you accept an invite. Reach out to the hospital admin to change it.",
                fontSize = 11.sp,
                color = DoctorColors.TextLight,
            )
        }
    }

    if (showPhoneChange) {
        ConfirmDialog(
            title = "Change phone number?",
            message = "You'll be signed out and can sign in again using a new number. The new number must match the one your hospital admin entered for you in the invitation.",
            confirmLabel = "Sign out",
            danger = true,
            onDismiss = { showPhoneChange = false },
            onConfirm = {
                showPhoneChange = false
                onLogout()
            },
        )
    }

    if (showPrivacy) {
        InfoDialog(
            title = "Privacy & Terms",
            onDismiss = { showPrivacy = false },
        ) {
            Text(
                "MedHistry handles patient data under India's Digital Personal Data Protection Act, 2023. " +
                    "Doctor sessions, access logs, and briefings are stored securely and accessible to the patient.",
                fontSize = 13.sp,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Full policy and terms: medhistry.com/legal",
                fontSize = 12.sp,
                color = DoctorColors.TextSecondary,
            )
        }
    }

    if (showAbout) {
        InfoDialog(
            title = "About MedHistry",
            onDismiss = { showAbout = false },
        ) {
            Text(
                "MedHistry helps doctors get fast, structured patient briefings from the patient's own records.",
                fontSize = 13.sp,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            DetailRow("Version", "1.0.0")
            DetailRow("Build", "Doctor Android")
            Spacer(Modifier.height(8.dp))
            Text(
                "© Nolojik · support@medhistry.com",
                fontSize = 11.sp,
                color = DoctorColors.TextLight,
            )
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Log out?",
            message = "You'll need to sign in with your phone number and OTP the next time you open the app.",
            confirmLabel = "Log out",
            danger = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
        )
    }
}

// --- Helpers ---

private fun prettyPhoneForDisplay(phoneE164: String): String =
    if (phoneE164.startsWith("+91") && phoneE164.length == 13)
        "+91 ${phoneE164.substring(3, 8)} ${phoneE164.substring(8)}"
    else phoneE164

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
private fun StatPill(
    value: String,
    label: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = DoctorColors.Primary,
            )
        } else {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DoctorColors.Primary)
        }
        Spacer(Modifier.height(4.dp))
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = DoctorColors.TextLight,
            modifier = Modifier.width(100.dp),
        )
        Text(
            value,
            fontSize = 13.sp,
            color = DoctorColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = DoctorColors.Primary)
            }
        },
        containerColor = DoctorColors.Surface,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
        },
        text = {
            Text(message, fontSize = 13.sp, color = DoctorColors.TextSecondary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (danger) DoctorColors.Danger else DoctorColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DoctorColors.TextSecondary)
            }
        },
        containerColor = DoctorColors.Surface,
    )
}
