package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DoctorProfile
import com.medhistry.data.DoctorRegisterRequest
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Doctor OTP "verification" + account creation.
 *
 * OTP is cosmetic for now (the backend does not require a phone challenge for
 * doctor registration — the hospital admin already vetted the phone via the
 * invitation). Once the user enters 6 digits we fire off the real
 * [MedHistryApi.registerDoctor] call with the data collected in the prior
 * signup step, then hand the resulting [DoctorProfile] back to the caller.
 */
@Composable
fun DoctorOtpScreen(
    api: MedHistryApi,
    phoneNumber: String,
    inviteCode: String,
    name: String,
    specialization: String,
    regNumber: String,
    password: String,
    onBack: () -> Unit,
    onRegistered: (DoctorProfile) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // DEV: Auto-fill OTP after a short delay (no real OTP backend for doctors).
    LaunchedEffect(Unit) {
        delay(800)
        code = "123456"
    }

    // When 6 digits are entered, kick off registration (idempotent via registering flag).
    LaunchedEffect(code) {
        if (code.length == 6 && !registering && error == null) {
            registering = true
            scope.launch {
                try {
                    val phoneWithCountry = if (phoneNumber.startsWith("+")) phoneNumber
                    else "+91${phoneNumber.removePrefix("91").trim()}"
                    val resp = api.registerDoctor(
                        DoctorRegisterRequest(
                            inviteCode = inviteCode,
                            phone = phoneWithCountry,
                            name = name,
                            password = password,
                            specialisation = specialization.ifBlank { null },
                            licenseNumber = regNumber.ifBlank { null },
                        )
                    )
                    onRegistered(resp.doctor)
                } catch (e: Exception) {
                    error = MedHistryApi.friendlyMessage(e)
                    registering = false
                    // Clear code so the user can see the error and retry by re-entering.
                    code = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DoctorColors.Background),
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
                modifier = Modifier.clickable(enabled = !registering) { onBack() },
            )
        }
        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
            Text("Verify Phone", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DoctorColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a 6-digit code to +91 $phoneNumber",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
            )

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(6) { i ->
                    val ch = code.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DoctorColors.Surface)
                            .border(
                                1.5.dp,
                                if (ch.isNotEmpty()) DoctorColors.Primary else DoctorColors.Border,
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoctorColors.TextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (registering) {
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = DoctorColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Creating your account…",
                        fontSize = 13.sp,
                        color = DoctorColors.TextSecondary,
                    )
                }
            } else if (error != null) {
                Text(
                    error!!,
                    fontSize = 13.sp,
                    color = DoctorColors.Danger,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap the keypad to try again",
                    fontSize = 12.sp,
                    color = DoctorColors.TextLight,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Text(
                    "Resend code in 45s",
                    fontSize = 13.sp,
                    color = DoctorColors.TextLight,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Keypad (disabled while registering)
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "\u232B"),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(6.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (key.isNotEmpty()) DoctorColors.Surface else Color.Transparent)
                                .clickable(enabled = key.isNotEmpty() && !registering) {
                                    // Clear previous error on any keypad input.
                                    if (error != null) error = null
                                    when (key) {
                                        "\u232B" -> if (code.isNotEmpty()) code = code.dropLast(1)
                                        else -> if (code.length < 6) code += key
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoctorColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
