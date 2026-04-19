package com.medhistry.patient.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.patient.R

/**
 * 4-digit PIN screen — used for both setting a new PIN and entering an existing one.
 *
 * @param mode "set" for new users (shows "Create your PIN"), "login" for existing
 * @param onPinEntered called when 4 digits entered; the caller handles API calls
 */
@Composable
fun PinScreen(
    mode: String, // "set" or "login"
    phone: String,
    error: String?,
    isLoading: Boolean,
    onBack: (() -> Unit)? = null,
    onPinEntered: (pin: String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Reset pin when error changes
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
            confirmPin = ""
            isConfirming = false
        }
    }

    val title = if (mode == "set") {
        if (isConfirming) "Confirm your PIN" else "Create your PIN"
    } else {
        "Enter your PIN"
    }

    val subtitle = if (mode == "set") {
        if (isConfirming) "Enter the same 4-digit PIN again" else "Choose a 4-digit PIN to secure your account"
    } else {
        "Enter your 4-digit PIN to continue"
    }

    // Blend the system status bar with the dark gradient at the top of the
    // screen — otherwise the default light theme paints a harsh white strip
    // above our navy background. Light icons (darkIcons=false) keep the
    // time/battery legible against navy.
    StatusBarColor(color = MedHistryColors.NavyDark, darkIcons = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MedHistryColors.NavyDark, MedHistryColors.NavyLight),
                    startY = 0f,
                    endY = 600f,
                )
            )
            .statusBarsPadding()             // keep content below the status bar
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        if (onBack != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onBack() },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Back",
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // App logo
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = "MedHistry",
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "MedHistry",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.CyanGlow,
        )
        Spacer(Modifier.height(32.dp))

        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))

        Spacer(Modifier.height(32.dp))

        // 4 dots/circles showing entered digits
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            val currentPin = if (isConfirming) confirmPin else pin
            repeat(4) { i ->
                val filled = i < currentPin.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MedHistryColors.CyanGlow else Color.White.copy(alpha = 0.15f)
                        )
                        .border(2.dp, MedHistryColors.CyanGlow, CircleShape),
                )
            }
        }

        // Error messages
        val displayError = localError ?: error
        displayError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        if (isLoading) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MedHistryColors.CyanGlow,
            )
        }

        Spacer(Modifier.weight(1f))  // push keypad to the bottom

        // Numeric keypad
        PinKeypad(
            onDigit = { digit ->
                if (isLoading) return@PinKeypad
                localError = null

                if (mode == "set") {
                    if (!isConfirming) {
                        if (pin.length < 4) pin += digit.toString()
                        if (pin.length == 4) {
                            isConfirming = true
                        }
                    } else {
                        if (confirmPin.length < 4) confirmPin += digit.toString()
                        if (confirmPin.length == 4) {
                            if (confirmPin == pin) {
                                onPinEntered(pin)
                            } else {
                                localError = "Those PINs don't match — please try again."
                                pin = ""
                                confirmPin = ""
                                isConfirming = false
                            }
                        }
                    }
                } else {
                    // Login mode
                    if (pin.length < 4) pin += digit.toString()
                    if (pin.length == 4) {
                        onPinEntered(pin)
                    }
                }
            },
            onBackspace = {
                if (isLoading) return@PinKeypad
                if (mode == "set" && isConfirming) {
                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    else {
                        isConfirming = false
                        pin = pin.dropLast(1)
                    }
                } else {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                }
            },
        )
        Spacer(Modifier.navigationBarsPadding())  // clear Android gesture bar
    }
}

@Composable
private fun PinKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
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
                                if (key.isNotEmpty()) Color.White.copy(alpha = 0.1f)
                                else Color.Transparent
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
                                else Modifier
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
