package com.medhistry.doctor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DoctorProfile
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Minimal doctor sign-in. Uses /doctors/login — no registration flow here;
 * doctors are onboarded by hospital admin.
 */
@Composable
fun DoctorLoginScreen(
    api: MedHistryApi,
    onLoggedIn: (doctor: DoctorProfile) -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = DoctorColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "MedHistry",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.Primary,
            )
            Text(
                "Doctor sign-in",
                fontSize = 14.sp,
                color = DoctorColors.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = DoctorColors.Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            val res = api.loginDoctor(phone.trim(), password)
                            onLoggedIn(res.doctor)
                        } catch (e: Exception) {
                            error = e.message ?: "Sign-in failed"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = phone.isNotBlank() && password.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = DoctorColors.Primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text("Sign in", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
