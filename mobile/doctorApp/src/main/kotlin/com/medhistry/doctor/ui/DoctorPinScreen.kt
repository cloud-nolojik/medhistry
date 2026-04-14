package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 6-digit PIN screen for doctors. Two modes:
 *  - "set":   pick a PIN, confirm, save. Used right after first OTP login
 *             and after a Forgot-PIN → re-OTP recovery.
 *  - "login": enter existing PIN on app open / resume-after-background.
 *             Shows "Forgot PIN?" escape hatch that routes to re-OTP.
 *
 * Caller owns the API call and passes [error]/[isLoading] for state and
 * [onPinEntered] to handle submission (and [onForgot] in login mode).
 */
@Composable
fun DoctorPinScreen(
    mode: String,                 // "set" or "login"
    phonePretty: String,          // e.g. "+91 98123 45678" — just for display
    error: String?,
    isLoading: Boolean,
    onBack: (() -> Unit)? = null,
    onPinEntered: (pin: String) -> Unit,
    onForgot: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // When an external error comes in, wipe any entered digits so the user
    // can retype cleanly.
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
            confirmPin = ""
            isConfirming = false
        }
    }

    val title = when {
        mode == "set" && isConfirming -> "Confirm your PIN"
        mode == "set" -> "Create your PIN"
        else -> "Enter your PIN"
    }
    val subtitle = when {
        mode == "set" && isConfirming -> "Re-enter the same 6-digit PIN"
        mode == "set" -> "Choose a 6-digit PIN to unlock MedHistry"
        else -> "Enter your 6-digit PIN to continue"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DoctorColors.NavyDark, DoctorColors.NavyLight),
                    startY = 0f,
                    endY = 900f,
                ),
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        if (onBack != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "\u2039 Back",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onBack() },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(40.dp))
        Text(
            "MedHistry",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DoctorColors.CyanGlow,
        )
        Text(
            "Doctor",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(32.dp))

        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f))
        if (phonePretty.isNotEmpty() && mode == "login") {
            Spacer(Modifier.height(4.dp))
            Text(phonePretty, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }

        Spacer(Modifier.height(28.dp))

        // 6 dots
        val currentPin = if (isConfirming) confirmPin else pin
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            repeat(6) { i ->
                val filled = i < currentPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) DoctorColors.CyanGlow else Color.White.copy(alpha = 0.15f),
                        )
                        .border(2.dp, DoctorColors.CyanGlow, CircleShape),
                )
            }
        }

        val displayError = localError ?: error
        displayError?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                it,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (isLoading) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = DoctorColors.CyanGlow,
            )
        }

        Spacer(Modifier.height(24.dp))

        DoctorPinKeypad(
            onDigit = { digit ->
                if (isLoading) return@DoctorPinKeypad
                localError = null

                if (mode == "set") {
                    if (!isConfirming) {
                        if (pin.length < 6) pin += digit.toString()
                        if (pin.length == 6) isConfirming = true
                    } else {
                        if (confirmPin.length < 6) confirmPin += digit.toString()
                        if (confirmPin.length == 6) {
                            if (confirmPin == pin) {
                                onPinEntered(pin)
                            } else {
                                localError = "PINs don't match. Try again."
                                pin = ""
                                confirmPin = ""
                                isConfirming = false
                            }
                        }
                    }
                } else {
                    if (pin.length < 6) pin += digit.toString()
                    if (pin.length == 6) onPinEntered(pin)
                }
            },
            onBackspace = {
                if (isLoading) return@DoctorPinKeypad
                if (mode == "set" && isConfirming) {
                    if (confirmPin.isNotEmpty()) {
                        confirmPin = confirmPin.dropLast(1)
                    } else {
                        isConfirming = false
                        pin = pin.dropLast(1)
                    }
                } else {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                }
            },
        )

        if (mode == "login" && onForgot != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Forgot PIN? Sign in with OTP",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DoctorColors.CyanGlow,
                modifier = Modifier.clickable(enabled = !isLoading) { onForgot() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DoctorPinKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "\u232B"),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (key.isNotEmpty()) Color.White.copy(alpha = 0.10f)
                                else Color.Transparent,
                            )
                            .then(
                                if (key.isNotEmpty())
                                    Modifier
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(16.dp),
                                        )
                                        .clickable {
                                            when (key) {
                                                "\u232B" -> onBackspace()
                                                else -> key.toIntOrNull()?.let { onDigit(it) }
                                            }
                                        }
                                else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
