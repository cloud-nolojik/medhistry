package com.medhistry.patient.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * New user profile screen — shown after OTP verification for new users.
 * Collects name + optional details, then proceeds to PIN setup.
 */
@Composable
fun NewUserProfileScreen(
    phone: String,
    initialName: String = "",
    onBack: () -> Unit,
    onContinue: (name: String, dob: String?, gender: String?, bloodGroup: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(40.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
            "Complete your profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextPrimary,
        )
        Text(
            "Tell us about yourself",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))

        UppercaseLabel("Full Name *")
        LabeledBox {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = MedHistryColors.TextPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (name.isEmpty()) Text("Enter your name", color = MedHistryColors.TextLight)
                    inner()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        UppercaseLabel("Date of Birth")
        val context = LocalContext.current
        LabeledBox {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                dob = "%04d-%02d-%02d".format(year, month + 1, day)
                            },
                            cal.get(Calendar.YEAR) - 30,
                            0,
                            1,
                        ).show()
                    },
            ) {
                Text(
                    text = dob.ifEmpty { "Tap to select date" },
                    color = if (dob.isEmpty()) MedHistryColors.TextLight else MedHistryColors.TextPrimary,
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        UppercaseLabel("Gender")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Male", "Female", "Other").forEach { option ->
                val selected = gender == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MedHistryColors.Primary else MedHistryColors.Surface)
                        .clickable { gender = option }
                        .padding(vertical = 12.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        option,
                        color = if (selected) Color.White else MedHistryColors.TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        UppercaseLabel("Blood Group")
        val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bloodGroups.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { option ->
                        val selected = bloodGroup == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MedHistryColors.Primary else MedHistryColors.Surface)
                                .clickable { bloodGroup = if (selected) "" else option }
                                .padding(vertical = 12.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Text(
                                option,
                                color = if (selected) Color.White else MedHistryColors.TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                onContinue(
                    name.trim(),
                    dob.trim().ifEmpty { null },
                    gender.ifEmpty { null },
                    bloodGroup.ifEmpty { null },
                )
            },
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Continue to Set PIN", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(32.dp))
    }
}
