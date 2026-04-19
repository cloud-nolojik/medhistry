package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.medhistry.data.MedHistryApi
import com.medhistry.data.MedHistryApi.Companion.friendlyMessage
import kotlinx.coroutines.launch

/**
 * 4-digit OTP screen. Calls send-otp on launch (autofills in dev mode),
 * then verify-otp when 4 digits are entered.
 *
 * [onVerified] receives (tempToken, isNewUser).
 */
@Composable
fun OtpScreen(
    api: MedHistryApi,
    phoneNumber: String,
    onBack: () -> Unit,
    onVerified: (tempToken: String, isNewUser: Boolean) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableIntStateOf(30) }
    val scope = rememberCoroutineScope()

    // Send OTP on first load — autofill if dev mode returns it
    LaunchedEffect(Unit) {
        try {
            Log.d("OtpScreen", "Sending OTP to +91$phoneNumber")
            val result = api.sendOTP("+91$phoneNumber")
            Log.d("OtpScreen", "OTP response: message=${result.message}, otp=${result.otp}")
            // In dev mode, the OTP comes back in the response — autofill it
            result.otp?.let { devOtp ->
                code = devOtp
            }
        } catch (e: Exception) {
            Log.e("OtpScreen", "Failed to send OTP", e)
            error = friendlyMessage(e)
        }
    }

    // Countdown timer for resend
    LaunchedEffect(resendTimer) {
        if (resendTimer > 0) {
            kotlinx.coroutines.delay(1000)
            resendTimer--
        }
    }

    // Auto-verify when 4 digits entered
    LaunchedEffect(code) {
        if (code.length == 4 && !isLoading) {
            isLoading = true
            error = null
            try {
                val result = api.verifyOTP("+91$phoneNumber", code)
                onVerified(result.tempToken, result.isNewUser)
            } catch (e: Exception) {
                error = friendlyMessage(e)
                code = ""
                isLoading = false
            }
        }
    }

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
            Text("Back", color = MedHistryColors.TextPrimary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Let's confirm it's you",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextPrimary,
        )
        Text(
            "Enter the 4-digit code we sent to +91 $phoneNumber",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "It may arrive by SMS or WhatsApp",
            fontSize = 12.sp,
            color = MedHistryColors.TextLight,
        )
        Spacer(Modifier.height(32.dp))

        // 4 digit boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            repeat(4) { i ->
                val digit = code.getOrNull(i)?.toString() ?: ""
                val focused = i == code.length
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (digit.isNotEmpty()) MedHistryColors.PrimaryLight else MedHistryColors.Surface
                        )
                        .border(
                            2.dp,
                            if (focused || digit.isNotEmpty()) MedHistryColors.Primary else MedHistryColors.Border,
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        digit,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedHistryColors.TextPrimary,
                    )
                }
            }
        }

        // Error message
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFDC2626), fontSize = 13.sp)
        }

        // Loading indicator
        if (isLoading) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MedHistryColors.Primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Verifying...", color = MedHistryColors.TextSecondary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (resendTimer > 0) {
                Text("Resend available in ", color = MedHistryColors.TextSecondary, fontSize = 14.sp)
                Text(
                    "0:${resendTimer.toString().padStart(2, '0')}",
                    color = MedHistryColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    "Resend code",
                    color = MedHistryColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        resendTimer = 30
                        code = ""
                        error = null
                        scope.launch {
                            try {
                                val result = api.sendOTP("+91$phoneNumber")
                                result.otp?.let { code = it }
                            } catch (_: Exception) { }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        NumericKeypad(
            onDigit = { if (code.length < 4 && !isLoading) code += it.toString() },
            onBackspace = { if (code.isNotEmpty() && !isLoading) code = code.dropLast(1) },
        )
    }
}

@Composable
private fun NumericKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "\u232B"),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (key.isNotEmpty()) MedHistryColors.Surface else Color.Transparent
                            )
                            .then(
                                if (key.isNotEmpty())
                                    Modifier.clickable {
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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MedHistryColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}
