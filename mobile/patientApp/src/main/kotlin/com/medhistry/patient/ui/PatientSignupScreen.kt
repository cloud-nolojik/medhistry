package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
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
 * "Create your account" — full name + phone, "Send verification code" CTA.
 * Can also jump to login.
 */
@Composable
fun PatientSignupScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onContinue: (name: String, phone: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Back", color = MedHistryColors.TextPrimary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Create your account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextPrimary,
        )
        Text(
            "We'll send you a verification code",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
        )
        Spacer(Modifier.height(32.dp))

        UppercaseLabel("Full Name")
        LabeledBox(
            content = {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = MedHistryColors.TextPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text("Enter your name", color = MedHistryColors.TextLight)
                        }
                        inner()
                    },
                )
            },
        )

        Spacer(Modifier.height(20.dp))
        UppercaseLabel("Phone Number")
        LabeledBox(
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "+91",
                        color = MedHistryColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MedHistryColors.Border),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = phone,
                        onValueChange = { raw -> phone = raw.filter { it.isDigit() }.take(10) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        textStyle = TextStyle(fontSize = 16.sp, color = MedHistryColors.TextPrimary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (phone.isEmpty()) {
                                Text("Phone number", color = MedHistryColors.TextLight)
                            }
                            inner()
                        },
                    )
                }
            },
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onContinue(name.trim(), phone) },
            enabled = name.isNotBlank() && phone.length == 10,
            colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Send Verification Code", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Already have an account? ", color = MedHistryColors.TextSecondary, fontSize = 14.sp)
            Text(
                "Log in",
                color = MedHistryColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onLogin() },
            )
        }
    }
}

@Composable
fun PatientLoginScreen(
    onBack: () -> Unit,
    onSignup: () -> Unit,
    onContinue: (phone: String) -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Back", color = MedHistryColors.TextPrimary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Welcome back", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
        Text("Log in to your MedHistry account", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
        Spacer(Modifier.height(32.dp))
        UppercaseLabel("Phone Number")
        LabeledBox(
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+91", color = MedHistryColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MedHistryColors.Border),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = phone,
                        onValueChange = { raw -> phone = raw.filter { it.isDigit() }.take(10) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        textStyle = TextStyle(fontSize = 16.sp, color = MedHistryColors.TextPrimary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (phone.isEmpty()) {
                                Text("Phone number", color = MedHistryColors.TextLight)
                            }
                            inner()
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { onContinue(phone) },
            enabled = phone.length == 10,
            colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Send verification code", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("New here? ", color = MedHistryColors.TextSecondary, fontSize = 14.sp)
            Text(
                "Create account",
                color = MedHistryColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onSignup() },
            )
        }
    }
}

/**
 * Field label used above text inputs.
 *
 * Originally rendered as ALL-CAPS 12sp (the SmallCaps look). UX review flagged
 * that all-caps labels feel formal/hospital-y and harder to scan at a glance —
 * switched to sentence case at 13sp to read more like a natural prompt. Name
 * is retained (instead of FieldLabel) so we don't churn every callsite, even
 * though the label is no longer uppercased.
 */
@Composable
fun UppercaseLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MedHistryColors.TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun LabeledBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        content()
    }
}
