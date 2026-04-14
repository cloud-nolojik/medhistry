package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Entry point for doctor auth — collect phone number and request an OTP.
 *
 * The flow branches on the OTP verify response: returning doctors are logged
 * in directly; new doctors are routed through invite-code + signup.
 */
@Composable
fun DoctorPhoneEntryScreen(
    api: MedHistryApi,
    onBack: () -> Unit,
    onOtpSent: (phoneE164: String, devOtp: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
            Text(
                "Welcome",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your phone number to sign in or register",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
            )

            Spacer(Modifier.height(28.dp))

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
                Text(
                    "+91",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoctorColors.TextPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (phone.isEmpty()) {
                        Text("Phone number", fontSize = 15.sp, color = DoctorColors.TextLight)
                    }
                    BasicTextField(
                        value = phone,
                        onValueChange = {
                            phone = it.filter(Char::isDigit).take(10)
                            error = null
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = DoctorColors.TextPrimary, fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = DoctorColors.Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))

            val enabled = phone.length == 10 && !loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) DoctorColors.Primary else DoctorColors.Border)
                    .clickable(enabled = enabled) {
                        error = null
                        loading = true
                        val e164 = "+91$phone"
                        scope.launch {
                            try {
                                val resp = api.sendDoctorOTP(e164)
                                onOtpSent(e164, resp.otp)
                            } catch (e: Exception) {
                                error = MedHistryApi.friendlyMessage(e)
                            } finally {
                                loading = false
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        "Send OTP",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "We'll text a 4-digit code to verify this number.",
                fontSize = 12.sp,
                color = DoctorColors.TextLight,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
