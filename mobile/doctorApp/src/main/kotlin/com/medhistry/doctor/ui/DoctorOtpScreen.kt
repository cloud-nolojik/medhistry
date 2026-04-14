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
import com.medhistry.data.DoctorVerifyOTPResponse
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Doctor OTP verification screen.
 *
 * Given a phone number already sent an OTP via [MedHistryApi.sendDoctorOTP],
 * the user enters 4 digits which are verified against the backend.
 *
 * The returned [DoctorVerifyOTPResponse] is handed up via [onVerified]:
 *  - existing doctor (`isNewUser == false`) → response carries `accessToken`
 *    and `doctor` so the caller can go straight to Home.
 *  - new doctor (`isNewUser == true`) → response carries `tempToken` which
 *    must be passed to /doctors/complete-registration after invite + signup.
 *
 * [devOtpForAutofill] is the dev-only OTP returned by /send-otp. When set,
 * the screen autofills the code for convenience while SMS isn't wired up.
 */
@Composable
fun DoctorOtpScreen(
    api: MedHistryApi,
    phoneE164: String,
    devOtpForAutofill: String?,
    onBack: () -> Unit,
    onVerified: (DoctorVerifyOTPResponse) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // DEV: autofill OTP returned by /send-otp so the user doesn't have to
    // look at the server log. Remove this once SMS is wired up.
    LaunchedEffect(devOtpForAutofill) {
        if (!devOtpForAutofill.isNullOrBlank() && code.isEmpty()) {
            delay(600)
            code = devOtpForAutofill.take(4)
        }
    }

    // Auto-submit when 4 digits are entered.
    LaunchedEffect(code) {
        if (code.length == 4 && !verifying && error == null) {
            verifying = true
            scope.launch {
                try {
                    val resp = api.verifyDoctorOTP(phoneE164, code)
                    onVerified(resp)
                } catch (e: Exception) {
                    error = MedHistryApi.friendlyMessage(e)
                    verifying = false
                    code = ""
                }
            }
        }
    }

    // Pretty display of the phone for the subtitle: "+91 98765 43210"
    val prettyPhone = remember(phoneE164) {
        if (phoneE164.startsWith("+91") && phoneE164.length == 13) {
            "+91 ${phoneE164.substring(3, 8)} ${phoneE164.substring(8)}"
        } else phoneE164
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
                modifier = Modifier.clickable(enabled = !verifying) { onBack() },
            )
        }
        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(
                "Verify Phone",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a 4-digit code to $prettyPhone",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
            )

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) { i ->
                    val ch = code.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DoctorColors.Surface)
                            .border(
                                1.5.dp,
                                if (ch.isNotEmpty()) DoctorColors.Primary else DoctorColors.Border,
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoctorColors.TextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (verifying) {
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
                        "Verifying…",
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
                    "Didn't get a code? Go back and try again.",
                    fontSize = 13.sp,
                    color = DoctorColors.TextLight,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Keypad (disabled while verifying)
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
                                .clickable(enabled = key.isNotEmpty() && !verifying) {
                                    // Clear any prior error on keypad input
                                    if (error != null) error = null
                                    when (key) {
                                        "\u232B" -> if (code.isNotEmpty()) code = code.dropLast(1)
                                        else -> if (code.length < 4) code += key
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
