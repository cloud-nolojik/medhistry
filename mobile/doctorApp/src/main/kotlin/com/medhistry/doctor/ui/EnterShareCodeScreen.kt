package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.MedHistryApi
import com.medhistry.data.PatientBriefing
import kotlinx.coroutines.launch

/**
 * Doctor enters a 6-digit share code the patient read aloud.
 * Calls /qr/redeem-code and shows the briefing card on success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterShareCodeScreen(
    api: MedHistryApi,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var briefing by remember { mutableStateOf<PatientBriefing?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = DoctorColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (briefing != null) "Patient Briefing" else "Enter share code",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = DoctorColors.Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DoctorColors.Background,
                    titleContentColor = DoctorColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        val b = briefing
        if (b != null) {
            PatientBriefingCard(
                briefing = b,
                onDone = { briefing = null; code = ""; onBack() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    "Ask the patient to read their 6-digit code aloud.",
                    fontSize = 14.sp,
                    color = DoctorColors.TextSecondary,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        code = raw.filter { it.isDigit() }.take(6)
                    },
                    label = { Text("6-digit code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DoctorColors.Danger, fontSize = 13.sp)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            try {
                                briefing = api.redeemShareCode(code)
                            } catch (e: Exception) {
                                error = e.message ?: "Code not valid or expired"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = code.length == 6 && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = DoctorColors.Primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text("Verify code", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DoctorColors.PrimaryLight)
                        .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        "Codes expire 5 minutes after the patient generates them. " +
                            "If the code fails, ask the patient to regenerate it.",
                        fontSize = 12.sp,
                        color = DoctorColors.TextSecondary,
                    )
                }
            }
        }
    }
}
