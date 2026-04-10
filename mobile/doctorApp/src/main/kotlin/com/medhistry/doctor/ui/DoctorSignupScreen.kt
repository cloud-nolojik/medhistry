package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Doctor signup. Hospital is pre-filled (verified) from the prior invite code step.
 * Collects name, specialization, medical registration number and phone.
 */
@Composable
fun DoctorSignupScreen(
    hospital: String,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onContinue: (name: String, specialization: String, regNumber: String, phone: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var specialization by remember { mutableStateOf("") }
    var regNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

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
        }

        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(
                "Create Your Account",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your hospital has been verified",
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
                    Text("Verified", fontSize = 11.sp, color = DoctorColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))

            DoctorField("FULL NAME", name, "Enter your name") { name = it }
            Spacer(Modifier.height(16.dp))
            DoctorField("SPECIALIZATION", specialization, "e.g. Cardiology, Internal Medicine") { specialization = it }
            Spacer(Modifier.height(16.dp))
            DoctorField("MEDICAL REGISTRATION NO.", regNumber, "Registration number") { regNumber = it }
            Spacer(Modifier.height(16.dp))
            DoctorPhoneField(phone) { phone = it }

            Spacer(Modifier.height(32.dp))

            val enabled = name.isNotBlank() && specialization.isNotBlank() &&
                regNumber.isNotBlank() && phone.length == 10
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) DoctorColors.Primary else DoctorColors.Border)
                    .clickable(enabled = enabled) {
                        onContinue(name.trim(), specialization.trim(), regNumber.trim(), phone)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Already registered? ", fontSize = 14.sp, color = DoctorColors.TextSecondary)
                Text(
                    "Log In",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoctorColors.Primary,
                    modifier = Modifier.clickable { onLogin() },
                )
            }
        }
    }
}

@Composable
private fun DoctorField(
    label: String,
    value: String,
    placeholder: String,
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
                textStyle = TextStyle(
                    color = DoctorColors.TextPrimary,
                    fontSize = 15.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DoctorPhoneField(value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(
            "PHONE NUMBER",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = DoctorColors.TextLight,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DoctorColors.Surface)
                .border(1.dp, DoctorColors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+91", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorColors.TextPrimary)
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Phone number", fontSize = 15.sp, color = DoctorColors.TextLight)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it.filter(Char::isDigit).take(10)) },
                    singleLine = true,
                    textStyle = TextStyle(color = DoctorColors.TextPrimary, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
