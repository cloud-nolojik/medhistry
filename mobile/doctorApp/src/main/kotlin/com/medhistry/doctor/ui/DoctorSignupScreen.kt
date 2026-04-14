package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Doctor signup (new users only, after OTP + invite verification).
 *
 * Hospital is pre-filled (verified) from the prior invite-code step, and the
 * phone number was verified via OTP. The account is created server-side via
 * /doctors/complete-registration — there is no password (OTP-only auth).
 *
 * The only editable fields are name, specialization, and medical registration
 * number; the phone is displayed as confirmed info. The parent is responsible
 * for showing any inflight / error state during the API call ([submitting] and
 * [errorMessage]).
 */
@Composable
fun DoctorSignupScreen(
    hospital: String,
    phoneE164: String,
    prefillName: String = "",
    prefillSpecialisation: String = "",
    submitting: Boolean = false,
    errorMessage: String? = null,
    onBack: () -> Unit,
    onContinue: (name: String, specialization: String, regNumber: String) -> Unit,
) {
    var name by remember { mutableStateOf(prefillName) }
    var specialization by remember { mutableStateOf(prefillSpecialisation) }
    var regNumber by remember { mutableStateOf("") }

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
                modifier = Modifier.clickable(enabled = !submitting) { onBack() },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(
                "Complete Your Profile",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Just a few details to finish setting up",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
            )

            Spacer(Modifier.height(20.dp))

            // Verified hospital badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0FDF4))
                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DoctorColors.Accent),
                    contentAlignment = Alignment.Center,
                ) { Text("\u2713", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(hospital, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorColors.TextPrimary)
                    Text("Hospital verified", fontSize = 11.sp, color = DoctorColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Verified phone badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0FDF4))
                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DoctorColors.Accent),
                    contentAlignment = Alignment.Center,
                ) { Text("\u2713", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatPhone(phoneE164), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DoctorColors.TextPrimary)
                    Text("Phone verified", fontSize = 11.sp, color = DoctorColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))

            DoctorField("FULL NAME", name, "Enter your name", enabled = !submitting) { name = it }
            Spacer(Modifier.height(16.dp))
            DoctorField("SPECIALIZATION", specialization, "e.g. Cardiology, Internal Medicine", enabled = !submitting) { specialization = it }
            Spacer(Modifier.height(16.dp))
            DoctorField("MEDICAL REGISTRATION NO.", regNumber, "Registration number", enabled = !submitting) { regNumber = it }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage, color = DoctorColors.Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))

            val enabled = name.isNotBlank() && specialization.isNotBlank() &&
                regNumber.isNotBlank() && !submitting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) DoctorColors.Primary else DoctorColors.Border)
                    .clickable(enabled = enabled) {
                        onContinue(name.trim(), specialization.trim(), regNumber.trim())
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text("Create Account", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatPhone(phoneE164: String): String =
    if (phoneE164.startsWith("+91") && phoneE164.length == 13) {
        "+91 ${phoneE164.substring(3, 8)} ${phoneE164.substring(8)}"
    } else phoneE164

@Composable
private fun DoctorField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = DoctorColors.TextLight,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DoctorColors.Surface)
                .border(1.dp, DoctorColors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = DoctorColors.TextLight)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(
                    color = DoctorColors.TextPrimary,
                    fontSize = 15.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
