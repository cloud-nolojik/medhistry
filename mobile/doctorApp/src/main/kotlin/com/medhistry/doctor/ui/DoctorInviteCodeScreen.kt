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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Doctor onboarding: enter 8-character hospital invite code.
 * Calls the backend to verify the code and returns the hospital name.
 */
@Composable
fun DoctorInviteCodeScreen(
    api: MedHistryApi,
    onBack: () -> Unit,
    onVerified: (code: String, hospital: String, doctorName: String, specialisation: String, phone: String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

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
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Text(
                "Hospital Invite Code",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter the 8-character code your hospital admin shared with you.",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(32.dp))

            // Single BasicTextField with custom decoration showing 8 individual boxes
            BasicTextField(
                value = code,
                onValueChange = {
                    val clean = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8)
                    code = clean
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 0.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                decorationBox = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(8) { i ->
                            val ch = code.getOrNull(i)?.toString() ?: ""
                            val isCursor = i == code.length && code.length < 8
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DoctorColors.Surface)
                                    .border(
                                        1.5.dp,
                                        when {
                                            ch.isNotEmpty() -> DoctorColors.Primary
                                            isCursor -> DoctorColors.Primary.copy(alpha = 0.5f)
                                            else -> DoctorColors.Border
                                        },
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ch,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DoctorColors.TextPrimary,
                                )
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Example: JDVA9K2X",
                fontSize = 12.sp,
                color = DoctorColors.TextLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = DoctorColors.Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))

            // Verify button
            val enabled = code.length == 8 && !loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) DoctorColors.Primary else DoctorColors.Border)
                    .clickable(enabled = enabled) {
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                val result = api.verifyInviteCode(code)
                                onVerified(code, result.hospitalName, result.doctorName ?: "", result.specialisation ?: "", result.doctorPhone ?: "")
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
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Verify Code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
