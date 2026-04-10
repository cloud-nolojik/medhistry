package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Doctor OTP verification — 6-box display + on-screen numeric keypad.
 * Auto-fires [onVerified] when 6 digits are entered.
 */
@Composable
fun DoctorOtpScreen(
    phoneNumber: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    LaunchedEffect(code) { if (code.length == 6) onVerified() }

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
                modifier = Modifier.clickable { onBack() },
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
            Text(
                "Resend code in 45s",
                fontSize = 13.sp,
                color = DoctorColors.TextLight,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.weight(1f))

        // Keypad
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
                                .clickable(enabled = key.isNotEmpty()) {
                                    when (key) {
                                        "\u232B" -> if (code.isNotEmpty()) code = code.dropLast(1)
                                        else -> if (code.length < 6) code += key
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key,
                                fontSize = if (key == "\u232B") 22.sp else 22.sp,
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
